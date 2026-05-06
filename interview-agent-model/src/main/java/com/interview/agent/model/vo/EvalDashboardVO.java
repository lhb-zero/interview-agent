package com.interview.agent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评估仪表盘视图对象
 */
@Data
public class EvalDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据集总数 */
    private Integer totalDatasets;

    /** 实验总数 */
    private Integer totalExperiments;

    /** 测试用例总数 */
    private Integer totalTestCases;

    /** 最近实验列表 */
    private List<ExperimentSummary> recentExperiments;

    @Data
    public static class ExperimentSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String name;
        private String status;
        private Double overallScore;
        private LocalDateTime createdAt;
    }
}
