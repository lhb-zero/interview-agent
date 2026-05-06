package com.interview.agent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评估结果明细实体
 */
@Data
@TableName("eval_result")
public class EvalResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联实验ID */
    private Long experimentId;

    /** 关联测试用例ID */
    private Long testCaseId;

    /** RAG生成的回答 */
    private String generatedAnswer;

    /** 检索到的上下文（JSON数组） */
    private String retrievedContexts;

    /** 改写后的查询 */
    private String rewrittenQuery;

    /** 检索精度得分 */
    private Double contextPrecision;

    /** 检索召心得分 */
    private Double contextRecall;

    /** 忠实度得分 */
    private Double faithfulness;

    /** 答案相关性得分 */
    private Double answerRelevancy;

    /** 精度计算详情（JSON） */
    private String precisionDetails;

    /** 召回计算详情（JSON） */
    private String recallDetails;

    /** 忠实度计算详情（JSON） */
    private String faithfulnessDetails;

    /** 相关性计算详情（JSON） */
    private String relevancyDetails;

    /** 检索耗时（毫秒） */
    private Integer retrievalTimeMs;

    /** 生成耗时（毫秒） */
    private Integer generationTimeMs;

    /** 评估耗时（毫秒） */
    private Integer evalTimeMs;

    /** 状态：PENDING/COMPLETED/FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
