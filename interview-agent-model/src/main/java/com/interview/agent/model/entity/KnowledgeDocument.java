package com.interview.agent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档实体
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档标题 */
    private String title;

    /** 领域：java/python/ai */
    private String domain;

    /** 文件类型：pdf/md/txt */
    private String fileType;

    /** 文件存储路径 */
    private String filePath;

    /** 分段数量 */
    private Integer chunkCount;

    /** 状态：ACTIVE/DELETED */
    @TableLogic(value = "ACTIVE", delval = "DELETED")
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
