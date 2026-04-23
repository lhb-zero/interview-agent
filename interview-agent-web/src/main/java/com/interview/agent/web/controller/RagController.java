package com.interview.agent.web.controller;

import com.interview.agent.common.result.Result;
import com.interview.agent.model.dto.ChatRequestDTO;
import com.interview.agent.model.vo.ChatMessageVO;
import com.interview.agent.service.rag.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * RAG 检索增强控制器
 */
@Tag(name = "RAG对话", description = "基于RAG检索增强的面试对话接口")
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @Operation(summary = "RAG增强对话（同步）")
    @PostMapping("/chat")
    public Result<ChatMessageVO> ragChat(@RequestBody ChatRequestDTO request) {
        request.setRagEnabled(true);
        return Result.success(ragService.ragChat(request));
    }

    @Operation(summary = "RAG增强流式对话（SSE）")
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ragChatStream(ChatRequestDTO request) {
        request.setRagEnabled(true);
        // 先获取或创建 sessionId，确保前端能第一时间拿到
        String sessionId = ragService.resolveSessionId(request);
        request.setSessionId(sessionId);
        // 在流的第一个事件中发送 sessionId
        Flux<String> sessionIdEvent = Flux.just("{\"sessionId\":\"" + sessionId + "\"}");
        Flux<String> contentStream = ragService.ragChatStream(request);
        return Flux.concat(sessionIdEvent, contentStream);
    }
}
