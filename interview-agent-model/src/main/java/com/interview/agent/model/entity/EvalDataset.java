package com.interview.agent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评估数据集实体
 */
@Data
@TableName("eval_dataset")
public class EvalDataset implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据集名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 领域：java/python/ai */
    private String domain;

    /** 测试用例数量 */
    private Integer testCaseCount;

    /** 状态：ACTIVE/DELETED */
    @TableLogic(value = "ACTIVE", delval = "DELETED")
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
