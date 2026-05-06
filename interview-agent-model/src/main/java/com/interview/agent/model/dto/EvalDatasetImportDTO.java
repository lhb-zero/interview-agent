package com.interview.agent.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 导入评估数据集请求 DTO
 */
@Data
public class EvalDatasetImportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据集名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 领域 */
    private String domain;

    /** 测试用例列表 */
    private List<TestCaseItem> testCases;

    @Data
    public static class TestCaseItem {
        /** 测试问题 */
        private String question;
        /** 标准答案 */
        private String groundTruthAnswer;
        /** 标准上下文列表 */
        private List<String> groundTruthContexts;
        /** 难度 */
        private String difficulty;
    }
}
