package com.interview.agent.service.rag;

import com.interview.agent.common.constant.CommonConstant;
import com.interview.agent.common.exception.BusinessException;
import com.interview.agent.common.result.ResultCode;
import com.interview.agent.dao.mapper.ChatMessageMapper;
import com.interview.agent.dao.mapper.ChatSessionMapper;
import com.interview.agent.dao.mapper.KnowledgeDocumentMapper;
import com.interview.agent.model.dto.ChatRequestDTO;
import com.interview.agent.model.entity.ChatMessage;
import com.interview.agent.model.entity.ChatSession;
import com.interview.agent.model.entity.KnowledgeDocument;
import com.interview.agent.model.vo.ChatMessageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenTextSplitter;
    private final KnowledgeDocumentMapper documentMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    @Value("classpath:/prompts/rag-enhanced-answer.st")
    private Resource ragPromptResource;

    @Override
    public ChatMessageVO ragChat(ChatRequestDTO request) {
        String sessionId = getOrCreateSession(request);

        // 1. 向量检索相关文档
        List<Document> similarDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.getMessage())
                        .topK(CommonConstant.RAG_DEFAULT_TOP_K)
                        .build()
        );

        // 2. 构建RAG增强Prompt
        Prompt prompt = buildRagPrompt(request, similarDocs);

        // 3. 保存用户消息（构建Prompt后再保存，避免历史消息重复）
        saveMessage(sessionId, CommonConstant.ROLE_USER, request.getMessage());

        // 3. 调用LLM
        String response;
        try {
            response = chatClient.prompt(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("RAG对话调用失败", e);
            throw new BusinessException(ResultCode.RAG_SEARCH_ERROR);
        }

        // 4. 保存助手消息
        saveMessage(sessionId, CommonConstant.ROLE_ASSISTANT, response);

        ChatMessageVO vo = new ChatMessageVO();
        vo.setSessionId(sessionId);
        vo.setRole(CommonConstant.ROLE_ASSISTANT);
        vo.setContent(response);
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }

    @Override
    public Flux<String> ragChatStream(ChatRequestDTO request) {
        // sessionId 已由 Controller 通过 resolveSessionId 提前设置到 request 中
        String sessionId = request.getSessionId();

        // 1. 向量检索
        List<Document> similarDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.getMessage())
                        .topK(CommonConstant.RAG_DEFAULT_TOP_K)
                        .build()
        );

        // 2. 构建RAG增强Prompt
        Prompt prompt = buildRagPrompt(request, similarDocs);

        // 3. 保存用户消息
        saveMessage(sessionId, CommonConstant.ROLE_USER, request.getMessage());

        // 4. 流式调用
        StringBuilder fullResponse = new StringBuilder();
        return chatClient.prompt(prompt)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> saveMessage(sessionId, CommonConstant.ROLE_ASSISTANT, fullResponse.toString()));
    }

    @Override
    public void importDocument(String filePath, String domain, String title) {
        try {
            // 1. 读取文档
            TextReader textReader = new TextReader(new org.springframework.core.io.FileSystemResource(filePath));
            List<Document> documents = textReader.get();

            // 2. 文本分块
            List<Document> chunks = tokenTextSplitter.apply(documents);

            // 3. 为每个块添加元数据
            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                chunk.getMetadata().put("domain", domain);
                chunk.getMetadata().put("title", title);
                chunk.getMetadata().put("chunk_index", i);
            }

            // 4. 存入向量数据库（自动Embedding）
            vectorStore.add(chunks);

            // 5. 保存文档记录
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setTitle(title);
            doc.setDomain(domain);
            doc.setFilePath(filePath);
            doc.setChunkCount(chunks.size());
            doc.setStatus("ACTIVE");
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.insert(doc);

            log.info("文档导入成功: title={}, domain={}, chunks={}", title, domain, chunks.size());
        } catch (Exception e) {
            log.error("文档导入失败: filePath={}", filePath, e);
            throw new BusinessException(ResultCode.DOCUMENT_PARSE_ERROR);
        }
    }

    @Override
    public void deleteDocument(Long documentId) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        doc.setStatus("DELETED");
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);
        log.info("文档已标记删除: id={}", documentId);
    }

    @Override
    public String resolveSessionId(ChatRequestDTO request) {
        return getOrCreateSession(request);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建RAG增强Prompt
     */
    private Prompt buildRagPrompt(ChatRequestDTO request, List<Document> similarDocs) {
        // 拼接检索到的文档作为上下文
        String context = similarDocs.stream()
                .map(doc -> {
                    String domain = (String) doc.getMetadata().getOrDefault("domain", "");
                    return "[" + domain + "] " + doc.getText();
                })
                .collect(Collectors.joining("\n\n"));

        // 使用RAG专用模板
        PromptTemplate template = new PromptTemplate(ragPromptResource);
        String systemContent = template.render(Map.of(
                "context", context,
                "question", request.getMessage()
        ));

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemContent));
        messages.add(new UserMessage(request.getMessage()));

        // 构建 OllamaChatOptions，控制深度思考模式（默认关闭）
        OllamaChatOptions.Builder optionsBuilder = OllamaChatOptions.builder()
                .model(CommonConstant.DEFAULT_CHAT_MODEL);
        if (Boolean.TRUE.equals(request.getThinkingEnabled())) {
            optionsBuilder.enableThinking();
        } else {
            optionsBuilder.disableThinking();
        }

        return new Prompt(messages, optionsBuilder.build());
    }

    private String getOrCreateSession(ChatRequestDTO request) {
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            return request.getSessionId();
        }
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setDomain(request.getDomain());
        session.setModelName(CommonConstant.DEFAULT_CHAT_MODEL);
        session.setCreatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return sessionId;
    }

    private void saveMessage(String sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }
}
