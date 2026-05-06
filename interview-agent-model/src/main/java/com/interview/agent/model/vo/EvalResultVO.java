package com.interview.agent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 评估结果明细视图对象
 */
@Data
public class EvalResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long experimentId;
    private Long testCaseId;
    private String question;
    private String groundTruthAnswer;
    private String generatedAnswer;
    private List<String> retrievedContexts;
    private String rewrittenQuery;
    private Double contextPrecision;
    private Double contextRecall;
    private Double faithfulness;
    private Double answerRelevancy;
    private Map<String, Object> precisionDetails;
    private Map<String, Object> recallDetails;
    private Map<String, Object> faithfulnessDetails;
    private Map<String, Object> relevancyDetails;
    private Integer retrievalTimeMs;
    private Integer generationTimeMs;
    private Integer evalTimeMs;
    private String status;
    private String errorMessage;
}
