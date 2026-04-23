package com.interview.agent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面试题收藏实体
 */
@Data
@TableName("favorite_question")
public class FavoriteQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联会话ID */
    private String sessionId;

    /** 面试题内容 */
    private String question;

    /** 参考答案 */
    private String answer;

    /** 技术领域 */
    private String domain;

    /** 难度级别：基础/中级/高级 */
    private String difficulty;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
