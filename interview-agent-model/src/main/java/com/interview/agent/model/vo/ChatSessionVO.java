package com.interview.agent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话会话 VO
 */
@Data
public class ChatSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String sessionId;

    private String title;

    private String domain;

    private String modelName;

    private LocalDateTime createdAt;

    /** 最近消息列表 */
    private List<ChatMessageVO> recentMessages;
}
