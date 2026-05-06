package com.interview.agent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评估实验实体
 */
@Data
@TableName("eval_experiment")
public class EvalExperiment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 实验名称 */
    private String name;

    /** 关联数据集ID */
    private Long datasetId;

    /** 实验配置快照：是否启用查询改写 */
    private Boolean queryRewriteEnabled;

    /** 实验配置快照：是否启用混合检索 */
    private Boolean hybridSearchEnabled;

    /** 实验配置快照：是否启用Reranker */
    private Boolean rerankerEnabled;

    /** 平均检索精度 */
    private Double avgContextPrecision;

    /** 平均检索召回 */
    private Double avgContextRecall;

    /** 平均忠实度 */
    private Double avgFaithfulness;

    /** 平均答案相关性 */
    private Double avgAnswerRelevancy;

    /** 综合分 */
    private Double overallScore;

    /** 总用例数 */
    private Integer totalCases;

    /** 已完成数 */
    private Integer completedCases;

    /** 失败数 */
    private Integer failedCases;

    /** 状态：PENDING/RUNNING/COMPLETED/FAILED/CANCELLED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
