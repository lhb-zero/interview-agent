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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对话服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    @Value("classpath:/prompts/interview-system.st")
    private Resource systemPromptResource;

    @Override
    public ChatMessageVO chat(ChatRequestDTO request) {
        // 1. 获取或创建会话
        String sessionId = getOrCreateSession(request);

        // 2. 构建Prompt（先构建，再保存消息，避免历史消息重复）
        Prompt prompt = buildPrompt(request);

        // 3. 保存用户消息
        saveMessage(sessionId, CommonConstant.ROLE_USER, request.getMessage());

        // 4. 调用LLM
        String response;
        try {
            response = chatClient.prompt(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("LLM调用失败", e);
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

        // 4. 流式调用LLM
        StringBuilder fullResponse = new StringBuilder();
        return chatClient.prompt(prompt)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    // 5. 流式完成后保存完整助手消息
                    saveMessage(sessionId, CommonConstant.ROLE_ASSISTANT, fullResponse.toString());
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
        session.setModelName(CommonConstant.DEFAULT_CHAT_MODEL);
        session.setCreatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return sessionId;
    }

    /**
     * 构建Prompt（使用外部化模板）
     */
    private Prompt buildPrompt(ChatRequestDTO request) {
        // 使用 PromptTemplate 从 .st 文件加载
        PromptTemplate template = new PromptTemplate(systemPromptResource);
        String systemContent = template.render(Map.of(
                "domain", request.getDomain() != null ? request.getDomain() : "Java",
                "difficulty", request.getDifficulty() != null ? request.getDifficulty() : "中级"
        ));

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemContent));

        // 加载历史消息（不包含当前用户消息，因为当前消息尚未保存或已单独添加）
        if (request.getSessionId() != null) {
            List<ChatMessage> history = messageMapper.selectList(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, request.getSessionId())
                            .orderByAsc(ChatMessage::getCreatedAt)
                            .last("LIMIT 20") // 最近20条
            );
            for (ChatMessage msg : history) {
                if (CommonConstant.ROLE_USER.equals(msg.getRole())) {
                    messages.add(new UserMessage(msg.getContent()));
                } else if (CommonConstant.ROLE_ASSISTANT.equals(msg.getRole())) {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }

        // 添加当前用户消息
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
