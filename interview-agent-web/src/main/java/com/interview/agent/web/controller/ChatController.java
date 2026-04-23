package com.interview.agent.web.controller;

import com.interview.agent.common.result.Result;
import com.interview.agent.model.dto.ChatRequestDTO;
import com.interview.agent.model.vo.ChatMessageVO;
import com.interview.agent.model.vo.ChatSessionVO;
import com.interview.agent.service.chat.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 对话控制器
 */
@Tag(name = "对话管理", description = "面试对话相关接口")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "发送对话消息（同步）")
    @PostMapping("/send")
    public Result<ChatMessageVO> chat(@RequestBody ChatRequestDTO request) {
        return Result.success(chatService.chat(request));
    }

    @Operation(summary = "流式对话（SSE）")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(ChatRequestDTO request) {
        // 先获取或创建 sessionId，确保前端能第一时间拿到
        String sessionId = chatService.resolveSessionId(request);
        // 将 sessionId 回写到 request 中，避免 chatStream 内部重复创建
        request.setSessionId(sessionId);
        // 在流的第一个事件中发送 sessionId
        Flux<String> sessionIdEvent = Flux.just("{\"sessionId\":\"" + sessionId + "\"}");
        // 后续是 LLM 流式输出
        Flux<String> contentStream = chatService.chatStream(request);
        return Flux.concat(sessionIdEvent, contentStream);
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> listSessions() {
        return Result.success(chatService.listSessions());
    }

    @Operation(summary = "获取会话消息历史")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> listMessages(@PathVariable String sessionId) {
        return Result.success(chatService.listMessages(sessionId));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        chatService.deleteSession(sessionId);
        return Result.success();
    }

    @Operation(summary = "重命名会话")
    @PutMapping("/sessions/{sessionId}/rename")
    public Result<Void> renameSession(@PathVariable String sessionId, @RequestBody Map<String, String> body) {
        chatService.renameSession(sessionId, body.get("title"));
        return Result.success();
    }
}
