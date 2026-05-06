package com.interview.agent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评估测试用例实体
 */
@Data
@TableName("eval_test_case")
public class EvalTestCase implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联数据集ID */
    private Long datasetId;

    /** 测试问题 */
    private String question;

    /** 标准答案 */
    private String groundTruthAnswer;

    /** 标准上下文（JSON数组） */
    private String groundTruthContexts;

    /** 难度：基础/中级/高级 */
    private String difficulty;

    /** 领域 */
    private String domain;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
