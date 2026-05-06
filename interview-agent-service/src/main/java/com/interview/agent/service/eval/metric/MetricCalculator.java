package com.interview.agent.service.eval.metric;

import java.util.List;

/**
 * RAG 评估指标计算器接口
 */
public interface MetricCalculator {

    /**
     * 指标名称
     */
    String metricName();

    /**
     * 计算单条评估结果的指标分数
     *
     * @param question          用户问题
     * @param generatedAnswer   RAG 生成的回答
     * @param contexts          检索到的上下文列表
     * @param groundTruth       标准答案
     * @param groundTruthContexts 标准上下文列表
     * @return 指标计算结果
     */
    MetricScore calculate(String question, String generatedAnswer,
                          List<String> contexts, String groundTruth,
                          List<String> groundTruthContexts);
}
