package com.interview.agent.service.chat;

import com.interview.agent.model.dto.ChatRequestDTO;
import com.interview.agent.model.vo.ChatMessageVO;
import com.interview.agent.model.vo.ChatSessionVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话服务接口
 */
public interface ChatService {

    /**
     * 发送对话消息（同步）
     */
    ChatMessageVO chat(ChatRequestDTO request);

    /**
     * 发送对话消息（流式 SSE）
     */
    Flux<String> chatStream(ChatRequestDTO request);

    /**
     * 获取会话列表
     */
    List<ChatSessionVO> listSessions();

    /**
     * 获取会话消息历史
     */
    List<ChatMessageVO> listMessages(String sessionId);

    /**
     * 删除会话
     */
    void deleteSession(String sessionId);

    /**
     * 重命名会话
     */
    void renameSession(String sessionId, String title);

    /**
     * 获取或创建会话ID（供 Controller 在流式响应前获取 sessionId）
     */
    String resolveSessionId(ChatRequestDTO request);
}
