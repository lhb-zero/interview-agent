package com.interview.agent.service.chat;

import com.interview.agent.common.constant.CommonConstant;
import com.interview.agent.common.exception.BusinessException;
import com.interview.agent.common.result.ResultCode;
import com.interview.agent.dao.mapper.ChatMessageMapper;
import com.interview.agent.dao.mapper.ChatSessionMapper;
import com.interview.agent.model.dto.ChatRequestDTO;
import com.interview.agent.model.entity.ChatMessage;
import com.interview.agent.model.entity.ChatSession;
import com.interview.agent.model.vo.ChatMessageVO;
import com.interview.agent.model.vo.ChatSessionVO;
import com.interview.agent.service.tool.KnowledgeSearchTool;
import com.interview.agent.service.tool.QuestionGenerateTool;
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
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对话服务实现
 *
 * 面试亮点：Tool Calling 动态注册
 * - 不在 SpringAiConfig 的 ChatClient Bean 中注册 Tool，避免循环依赖
 * - 在 Service 层通过 chatClient.prompt().tools(...) 按需动态注册
 * - LLM 自主决定是否调用工具，而非硬编码 if-else
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final KnowledgeSearchTool knowledgeSearchTool;
    private final QuestionGenerateTool questionGenerateTool;

    @Value("classpath:/prompts/interview-system.st")
    private Resource systemPromptResource;

    @Value("${spring.ai.ollama.chat.options.model}")
    private String modelName;

    @Override
    public ChatMessageVO chat(ChatRequestDTO request) {
        // 1. 获取或创建会话
        String sessionId = getOrCreateSession(request);

        // 2. 构建Prompt（先构建，再保存消息，避免历史消息重复）
        Prompt prompt = buildPrompt(request);

        // 3. 保存用户消息
        saveMessage(sessionId, CommonConstant.ROLE_USER, request.getMessage());

        // 4. 调用LLM（根据 ragEnabled 决定是否注册 Tool）
        String response;
        try {
            ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt(prompt);

            // 动态注册 Tool Calling — LLM 自主决定是否调用
            if (Boolean.TRUE.equals(request.getRagEnabled())) {
                requestSpec = requestSpec.tools(knowledgeSearchTool, questionGenerateTool);
                log.info("[Chat] Tool Calling 已注册: KnowledgeSearchTool, QuestionGenerateTool");
            }

            log.info("[Chat] 开始同步调用LLM: model={}, sessionId={}, ragEnabled={}", modelName, sessionId, request.getRagEnabled());
            response = requestSpec.call().content();
            log.info("[Chat] LLM同步响应完成: sessionId={}, 响应长度={}", sessionId, response != null ? response.length() : 0);
            log.debug("[Chat] LLM同步响应内容:\n{}", response);
        } catch (Exception e) {
            log.error("[Chat] LLM调用失败: sessionId={}", sessionId, e);
            throw new BusinessException(ResultCode.CHAT_ERROR);
        }

        // 5. 保存助手消息
        saveMessage(sessionId, CommonConstant.ROLE_ASSISTANT, response);

        // 6. 返回结果
        ChatMessageVO vo = new ChatMessageVO();
        vo.setSessionId(sessionId);
        vo.setRole(CommonConstant.ROLE_ASSISTANT);
        vo.setContent(response);
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }

    @Override
    public Flux<String> chatStream(ChatRequestDTO request) {
        // sessionId 已由 Controller 通过 resolveSessionId 提前设置到 request 中
        String sessionId = request.getSessionId();

        // 2. 构建Prompt（先构建，再保存消息，避免历史消息重复）
        Prompt prompt = buildPrompt(request);

        // 3. 保存用户消息
        saveMessage(sessionId, CommonConstant.ROLE_USER, request.getMessage());

        // 4. 流式调用LLM（根据 ragEnabled 决定是否注册 Tool）
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt(prompt);

        // 动态注册 Tool Calling — LLM 自主决定是否调用
        if (Boolean.TRUE.equals(request.getRagEnabled())) {
            requestSpec = requestSpec.tools(knowledgeSearchTool, questionGenerateTool);
            log.info("[ChatStream] Tool Calling 已注册: KnowledgeSearchTool, QuestionGenerateTool");
        }

        log.info("[ChatStream] 开始流式调用LLM: model={}, sessionId={}, ragEnabled={}", modelName, sessionId, request.getRagEnabled());

        StringBuilder fullResponse = new StringBuilder();
        return requestSpec
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    // 5. 流式完成后保存完整助手消息
                    saveMessage(sessionId, CommonConstant.ROLE_ASSISTANT, fullResponse.toString());
                    log.info("[ChatStream] LLM流式响应完成: sessionId={}, 响应长度={}", sessionId, fullResponse.length());
                    log.debug("[ChatStream] LLM流式响应内容:\n{}", fullResponse.toString());
                })
                .onErrorResume(e -> {
                    log.error("[ChatStream] LLM流式调用失败: sessionId={}, ragEnabled={}", sessionId, request.getRagEnabled(), e);
                    return Flux.just("抱歉，生成回答时出现错误，请稍后重试。");
                });
    }

    @Override
    public List<ChatSessionVO> listSessions() {
        List<ChatSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .orderByDesc(ChatSession::getCreatedAt)
        );
        return sessions.stream().map(this::toSessionVO).collect(Collectors.toList());
    }

    @Override
    public List<ChatMessageVO> listMessages(String sessionId) {
        List<ChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreatedAt)
        );
        return messages.stream().map(this::toMessageVO).collect(Collectors.toList());
    }

    @Override
    public void deleteSession(String sessionId) {
        sessionMapper.delete(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, sessionId));
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
    }

    @Override
    public void renameSession(String sessionId, String title) {
        ChatSession session = sessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
        );
        if (session != null) {
            session.setTitle(title);
            sessionMapper.updateById(session);
        }
    }

    @Override
    public String resolveSessionId(ChatRequestDTO request) {
        return getOrCreateSession(request);
    }

    // ==================== 私有方法 ====================

    /**
     * 获取或创建会话
     */
    private String getOrCreateSession(ChatRequestDTO request) {
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            return request.getSessionId();
        }
        // 新建会话
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setDomain(request.getDomain());
        session.setModelName(modelName);
        session.setCreatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return sessionId;
    }

    /**
     * 构建Prompt（使用外部化模板）
     */
    private Prompt buildPrompt(ChatRequestDTO request) {
        String templateContent;
        try {
            templateContent = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[Prompt] 读取系统模板文件失败", e);
            throw new BusinessException(ResultCode.CHAT_ERROR);
        }
        PromptTemplate template = new PromptTemplate(templateContent);
        String systemContent = template.render(Map.of(
                "domain", request.getDomain() != null ? request.getDomain() : "Java",
                "difficulty", request.getDifficulty() != null ? request.getDifficulty() : "中级"
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

        log.info("[Prompt] 构建完成: model={}, domain={}, difficulty={}, historyCount={}, totalMessages={}, userMessage='{}'",
                modelName, request.getDomain(), request.getDifficulty(), historyCount, messages.size(),
                request.getMessage().length() > 50 ? request.getMessage().substring(0, 50) + "..." : request.getMessage());
        log.debug("[Prompt] System Prompt内容:\n{}", systemContent);

        OllamaChatOptions.Builder optionsBuilder = OllamaChatOptions.builder();
        if (Boolean.TRUE.equals(request.getThinkingEnabled())) {
            optionsBuilder.enableThinking();
        } else {
            optionsBuilder.disableThinking();
        }

        return new Prompt(messages, optionsBuilder.build());
    }

    /**
     * 保存消息
     */
    private void saveMessage(String sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    private ChatSessionVO toSessionVO(ChatSession session) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setSessionId(session.getSessionId());
        vo.setTitle(session.getTitle());
        vo.setDomain(session.getDomain());
        vo.setModelName(session.getModelName());
        vo.setCreatedAt(session.getCreatedAt());
        return vo;
    }

    private ChatMessageVO toMessageVO(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setTokensUsed(message.getTokensUsed());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }
}
