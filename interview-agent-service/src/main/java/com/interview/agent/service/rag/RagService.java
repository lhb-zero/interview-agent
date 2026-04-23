package com.interview.agent.service.rag;

import com.interview.agent.model.dto.ChatRequestDTO;
import com.interview.agent.model.vo.ChatMessageVO;
import reactor.core.publisher.Flux;

/**
 * RAG 服务接口
 */
public interface RagService {

    /**
     * RAG 增强对话（同步）
     */
    ChatMessageVO ragChat(ChatRequestDTO request);

    /**
     * RAG 增强对话（流式 SSE）
     */
    Flux<String> ragChatStream(ChatRequestDTO request);

    /**
     * 导入文档到知识库
     */
    void importDocument(String filePath, String domain, String title);

    /**
     * 删除知识库文档
     */
    void deleteDocument(Long documentId);

    /**
     * 获取或创建会话ID（供 Controller 在流式响应前获取 sessionId）
     */
    String resolveSessionId(ChatRequestDTO request);
}
