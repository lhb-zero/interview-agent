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
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 服务实现
 *
 * 面试亮点：
 * 1. 文档导入全链路：文件解析 → Chunking → Embedding → PGVector 存储
 * 2. 多格式支持：PDF (PagePdfDocumentReader)、Markdown (TextReader)、TXT (TextReader)
 * 3. RAG 检索增强：问题向量化 → 相似度检索 → Prompt 拼接 → LLM 生成
 * 4. 相似度阈值过滤：低于阈值的文档片段不参与增强，避免噪声
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

    @Value("${spring.ai.ollama.chat.options.model}")
    private String modelName;

    /** 相似度阈值 — 低于此值的检索结果不参与增强（余弦相似度范围 -1~1，越高越相似） */
    private static final double SIMILARITY_THRESHOLD = CommonConstant.RAG_SIMILARITY_THRESHOLD;

    // ==================== RAG 对话 ====================

    @Override
    public ChatMessageVO ragChat(ChatRequestDTO request) {
        String sessionId = getOrCreateSession(request);

        // 1. 向量检索相关文档
        List<Document> similarDocs = searchSimilarDocs(request.getMessage(), request.getDomain());

        // 2. 构建RAG增强Prompt
        Prompt prompt = buildRagPrompt(request, similarDocs);

        // 3. 保存用户消息（构建Prompt后再保存，避免历史消息重复）
        saveMessage(sessionId, CommonConstant.ROLE_USER, request.getMessage());

        // 4. 调用LLM
        String response;
        try {
            log.info("[RAG-Chat] 开始同步调用LLM: model={}, sessionId={}, similarDocs={}", modelName, sessionId, similarDocs.size());
            response = chatClient.prompt(prompt)
                    .call()
                    .content();
            log.info("[RAG-Chat] LLM同步响应完成: sessionId={}, 响应长度={}", sessionId, response != null ? response.length() : 0);
            log.debug("[RAG-Chat] LLM同步响应内容:\n{}", response);
        } catch (Exception e) {
            log.error("[RAG-Chat] RAG对话调用失败: sessionId={}", sessionId, e);
            throw new BusinessException(ResultCode.RAG_SEARCH_ERROR);
        }

        // 5. 保存助手消息
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
        List<Document> similarDocs = searchSimilarDocs(request.getMessage(), request.getDomain());

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
                .doOnComplete(() -> {
                    saveMessage(sessionId, CommonConstant.ROLE_ASSISTANT, fullResponse.toString());
                    log.info("[RAG-Stream] LLM流式响应完成: sessionId={}, 响应长度={}", sessionId, fullResponse.length());
                    log.debug("[RAG-Stream] LLM流式响应内容:\n{}", fullResponse.toString());
                });
    }

    // ==================== 文档导入 ====================

    @Override
    public void importDocument(String filePath, String domain, String title) {
        importDocument(filePath, domain, title, null);
    }

    /**
     * 导入文档到知识库（完整版，支持文件类型识别）
     *
     * 核心流程（面试必背）：
     * ① 文件解析 — 根据文件后缀选择对应的 DocumentReader
     * ② 文本分块 (Chunking) — TokenTextSplitter 按 Token 数分段
     * ③ 向量化 (Embedding) — bge-m3 将文本转为 1024 维向量
     * ④ 存储 (VectorStore) — 向量 + 元数据写入 PGVector
     * ⑤ 记录 (RDBMS) — knowledge_document 表记录文档元信息
     */
    public void importDocument(String filePath, String domain, String title, String fileType) {
        try {
            // 识别文件类型
            String actualFileType = fileType != null ? fileType : detectFileType(filePath);
            log.info("开始导入文档: filePath={}, domain={}, title={}, fileType={}", filePath, domain, title, actualFileType);

            // ① 根据文件类型选择 Reader 解析文档
            List<Document> documents = parseDocument(filePath, actualFileType);
            log.info("文档解析完成: 共 {} 个原始文档段", documents.size());

            if (documents.isEmpty()) {
                throw new BusinessException(ResultCode.DOCUMENT_PARSE_ERROR);
            }

            // ② 文本分块 (Chunking)
            List<Document> chunks = tokenTextSplitter.apply(documents);
            log.info("文本分块完成: 共 {} 个分块（分块前）", chunks.size());

            // ③ 过滤过短的分块（bge-m3 对过短文本会返回 NaN 向量，导致 Ollama 报 500）
            // 面试亮点：这是 RAG 生产环境常见坑 —— 空内容/极短内容的 Embedding 不可靠
            int minChunkLength = 20;  // 低于 20 字符的块大概率无语义价值
            List<Document> validChunks = new ArrayList<>();
            for (Document chunk : chunks) {
                String text = chunk.getText();
                if (text != null && text.trim().length() >= minChunkLength) {
                    validChunks.add(chunk);
                } else {
                    log.debug("过滤短文本块: length={}, content='{}'",
                            text != null ? text.length() : 0,
                            text != null ? text.substring(0, Math.min(50, text.length())) : "null");
                }
            }
            log.info("过滤短文本块完成: 保留 {}/{} 个有效分块", validChunks.size(), chunks.size());

            if (validChunks.isEmpty()) {
                throw new BusinessException(ResultCode.DOCUMENT_PARSE_ERROR);
            }

            // ④ 为每个块添加元数据
            for (int i = 0; i < validChunks.size(); i++) {
                Document chunk = validChunks.get(i);
                chunk.getMetadata().put("domain", domain);
                chunk.getMetadata().put("title", title);
                chunk.getMetadata().put("file_type", actualFileType);
                chunk.getMetadata().put("chunk_index", i);
                chunk.getMetadata().put("total_chunks", validChunks.size());
            }

            // ⑤ 逐条存入向量数据库
            // 面试亮点：Ollama 0.21.0 的 /api/embed 批量接口有 NaN bug，逐条调用更稳定
            // 生产环境中 Embedding 导入要：逐条 + 重试 + 统计实际成功数
            int successCount = 0;
            int failCount = 0;
            for (int i = 0; i < validChunks.size(); i++) {
                Document chunk = validChunks.get(i);
                try {
                    vectorStore.add(List.of(chunk));
                    successCount++;
                    if ((i + 1) % 10 == 0 || (i + 1) == validChunks.size()) {
                        log.info("向量存储进度: {}/{}", i + 1, validChunks.size());
                    }
                } catch (Exception e) {
                    failCount++;
                    String preview = "";
                    String text = chunk.getText();
                    if (text != null) {
                        preview = text.substring(0, Math.min(50, text.length()));
                    }
                    log.warn("分块 {}/{} 向量存储失败: {} | content='{}'",
                            i + 1, validChunks.size(), e.getMessage(),
                            preview);
                }
            }

            // ⑥ 判断实际入库结果
            if (successCount == 0) {
                log.error("向量存储全部失败: 共 {} 个分块，0 个成功", validChunks.size());
                throw new BusinessException(ResultCode.VECTOR_STORE_ERROR);
            }
            if (failCount > 0) {
                log.warn("向量存储部分失败: 成功 {}/{}, 失败 {}", successCount, validChunks.size(), failCount);
            }
            log.info("向量存储完成: 成功 {}/{} 个分块已写入 PGVector", successCount, validChunks.size());

            // ⑦ 保存文档记录到关系数据库（记录实际成功数）
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setTitle(title);
            doc.setDomain(domain);
            doc.setFileType(actualFileType);
            doc.setFilePath(filePath);
            doc.setChunkCount(successCount);  // 记录实际成功入库的分块数
            doc.setStatus("ACTIVE");
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.insert(doc);

            log.info("文档导入成功: title={}, domain={}, fileType={}, 成功chunks={}, 失败chunks={}",
                    title, domain, actualFileType, successCount, failCount);
        } catch (BusinessException e) {
            throw e;
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
        // 注意：PGVector 中的向量数据未删除，后续可通过 doc_id 元数据批量清理
    }

    @Override
    public String resolveSessionId(ChatRequestDTO request) {
        return getOrCreateSession(request);
    }

    // ==================== 私有方法 ====================

    /**
     * 根据文件类型选择对应的 DocumentReader 解析文档
     *
     * 面试亮点：不同文件格式需要不同的解析策略
     * - PDF: PagePdfDocumentReader（基于 Apache PdfBox，按页提取）
     * - MD/TXT: TextReader（纯文本直接读取）
     */
    private List<Document> parseDocument(String filePath, String fileType) {
        Resource resource = new org.springframework.core.io.FileSystemResource(filePath);

        return switch (fileType.toLowerCase()) {
            case "pdf" -> {
                // PDF 按页解析，每 1 页为一个 Document
                PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                        .withPagesPerDocument(1)  // 每页作为一个独立文档段
                        .build();
                PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);
                yield pdfReader.get();
            }
            case "md", "txt" -> {
                // Markdown 和纯文本直接读取
                TextReader textReader = new TextReader(resource);
                yield textReader.get();
            }
            default -> {
                log.warn("不支持的文件类型: {}, 尝试作为纯文本读取", fileType);
                TextReader fallbackReader = new TextReader(resource);
                yield fallbackReader.get();
            }
        };
    }

    /**
     * 根据文件路径识别文件类型
     */
    private String detectFileType(String filePath) {
        if (filePath == null) return "txt";
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".pdf")) return "pdf";
        if (lowerPath.endsWith(".md") || lowerPath.endsWith(".markdown")) return "md";
        return "txt";
    }

    /**
     * 向量相似度检索（支持领域过滤 + 相似度阈值）
     *
     * 面试亮点：
     * 1. filterExpression — 基于元数据的领域过滤，缩小检索范围
     * 2. similarityThreshold — 相似度阈值过滤，避免引入不相关的噪声文档
     * 3. topK — 返回最相似的 K 个结果
     */
    private List<Document> searchSimilarDocs(String query, String domain) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(CommonConstant.RAG_DEFAULT_TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD);

        // 如果指定了领域，添加过滤条件
        if (domain != null && !domain.isEmpty()) {
            builder.filterExpression("domain == '" + domain + "'");
        }

        SearchRequest searchRequest = builder.build();
        log.info("[RAG-Search] 向量检索: query='{}', domain='{}', topK={}, threshold={}", query, domain, searchRequest.getTopK(), SIMILARITY_THRESHOLD);

        List<Document> results = vectorStore.similaritySearch(searchRequest);
        log.info("[RAG-Search] 检索完成: 命中 {} 条文档", results.size());

        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            String docDomain = (String) doc.getMetadata().getOrDefault("domain", "");
            String docTitle = (String) doc.getMetadata().getOrDefault("title", "");
            Object distance = doc.getMetadata().get("distance");
            String text = doc.getText();
            String preview = text != null ? text.substring(0, Math.min(100, text.length())) : "null";
            log.info("[RAG-Search] 结果[{}]: domain={}, title={}, distance={}, length={}, preview='{}'",
                    i, docDomain, docTitle, distance, text != null ? text.length() : 0, preview);
            log.debug("[RAG-Search] 结果[{}] 完整内容:\n{}", i, text);
        }

        return results;
    }

    /**
     * 构建RAG增强Prompt
     *
     * 面试亮点：RAG 的核心就是"检索结果注入 Prompt"
     * - System Prompt：角色设定 + 参考资料
     * - User Message：用户原始问题
     * - 检索到的文档片段作为 context 注入
     */
    private Prompt buildRagPrompt(ChatRequestDTO request, List<Document> similarDocs) {
        // 拼接检索到的文档作为上下文
        String context;
        if (similarDocs.isEmpty()) {
            context = "未检索到相关的参考资料，请基于你的知识回答。";
        } else {
            context = similarDocs.stream()
                    .map(doc -> {
                        String docDomain = (String) doc.getMetadata().getOrDefault("domain", "");
                        String docTitle = (String) doc.getMetadata().getOrDefault("title", "");
                        int chunkIndex = (int) doc.getMetadata().getOrDefault("chunk_index", 0);
                        return "【来源: " + docDomain + " / " + docTitle + " - 第" + (chunkIndex + 1) + "段】\n" + doc.getText();
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));
        }

        String templateContent;
        try {
            templateContent = ragPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[RAG-Prompt] 读取RAG模板文件失败", e);
            throw new BusinessException(ResultCode.CHAT_ERROR);
        }
        PromptTemplate template = new PromptTemplate(templateContent);
        String systemContent = template.render(Map.of(
                "context", context,
                "question", request.getMessage()
        ));

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemContent));

        int historyCount = 0;
        if (request.getSessionId() != null) {
            List<ChatMessage> history = messageMapper.selectList(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, request.getSessionId())
                            .orderByAsc(ChatMessage::getCreatedAt)
                            .last("LIMIT 20")
            );
            historyCount = history.size();
            for (ChatMessage msg : history) {
                if (CommonConstant.ROLE_USER.equals(msg.getRole())) {
                    messages.add(new UserMessage(msg.getContent()));
                } else if (CommonConstant.ROLE_ASSISTANT.equals(msg.getRole())) {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }

        messages.add(new UserMessage(request.getMessage()));

        log.info("[RAG-Prompt] 构建完成: model={}, similarDocs={}, contextLength={}, historyCount={}, totalMessages={}, userMessage='{}'",
                modelName, similarDocs.size(), context.length(), historyCount, messages.size(),
                request.getMessage().length() > 50 ? request.getMessage().substring(0, 50) + "..." : request.getMessage());
        log.debug("[RAG-Prompt] RAG上下文内容:\n{}", context);
        log.debug("[RAG-Prompt] System Prompt内容:\n{}", systemContent);

        OllamaChatOptions.Builder optionsBuilder = OllamaChatOptions.builder();
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
        session.setModelName(modelName);
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
