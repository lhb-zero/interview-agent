package com.interview.agent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评估实验视图对象
 */
@Data
public class EvalExperimentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Long datasetId;
    private String datasetName;
    private Boolean queryRewriteEnabled;
    private Boolean hybridSearchEnabled;
    private Boolean rerankerEnabled;
    private Double avgContextPrecision;
    private Double avgContextRecall;
    private Double avgFaithfulness;
    private Double avgAnswerRelevancy;
    private Double overallScore;
    private Integer totalCases;
    private Integer completedCases;
    private Integer failedCases;
    private String status;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    /** 进度百分比（计算字段） */
    public Double getProgressPercent() {
        if (totalCases == null || totalCases == 0) return 0.0;
        int done = (completedCases != null ? completedCases : 0) + (failedCases != null ? failedCases : 0);
        return Math.round(done * 1000.0 / totalCases) / 10.0;
    }
}
