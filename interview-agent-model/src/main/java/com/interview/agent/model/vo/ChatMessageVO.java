package com.interview.agent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话消息 VO
 */
@Data
public class ChatMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String sessionId;

    private String role;

    private String content;

    private Integer tokensUsed;

    private LocalDateTime createdAt;
}
