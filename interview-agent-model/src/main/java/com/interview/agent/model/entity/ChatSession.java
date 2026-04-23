package com.interview.agent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话会话实体
 */
@Data
@TableName("chat_session")
public class ChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID（UUID） */
    private String sessionId;

    /** 会话标题 */
    private String title;

    /** 面试领域 */
    private String domain;

    /** 使用的模型名称 */
    private String modelName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
