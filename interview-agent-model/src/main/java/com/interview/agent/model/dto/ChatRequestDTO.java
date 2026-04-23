package com.interview.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 对话请求 DTO
 */
@Data
public class ChatRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID（新建对话可不传） */
    private String sessionId;

    /** 用户消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 面试领域 */
    private String domain;

    /** 难度偏好 */
    private String difficulty;

    /** 是否启用 RAG */
    private Boolean ragEnabled = false;

    /** 是否启用深度思考（需模型支持，如 qwen3/deepseek-r1） */
    private Boolean thinkingEnabled = false;
}
