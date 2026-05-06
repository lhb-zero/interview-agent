package com.interview.agent.service.eval.metric;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Context Precision（检索精度）计算器
 * 对每个检索到的文档片段，用 LLM 判断是否与问题相关，然后计算加权精确率
 */
@Slf4j
@Component
public class ContextPrecisionCalculator implements MetricCalculator {

    private static final String SYSTEM_PROMPT = "你是一个检索质量评估助手。判断以下参考资料是否对回答用户问题有帮助。只回答 YES 或 NO，不要解释。";

    private final LlmJudgeClient judgeClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContextPrecisionCalculator(LlmJudgeClient judgeClient) {
        this.judgeClient = judgeClient;
    }

    @Override
    public String metricName() {
        return "context_precision";
    }

    @Override
    public MetricScore calculate(String question, String generatedAnswer,
                                  List<String> contexts, String groundTruth,
                                  List<String> groundTruthContexts) {
        if (contexts == null || contexts.isEmpty()) {
            return new MetricScore(0.0, "{}");
        }

        List<Boolean> relevanceJudgments = new ArrayList<>();
        for (String context : contexts) {
            String userPrompt = "【用户问题】\n" + question + "\n\n【参考资料】\n" + context;
            boolean relevant = judgeClient.judgeYesNo(SYSTEM_PROMPT, userPrompt);
            relevanceJudgments.add(relevant);
        }

        // 加权累积精确率（RAGAS 公式）
        double score = 0.0;
        int relevantCount = 0;
        for (int i = 0; i < relevanceJudgments.size(); i++) {
            if (relevanceJudgments.get(i)) {
                relevantCount++;
                double precisionAtI = (double) relevantCount / (i + 1);
                score += precisionAtI;
            }
        }
        score = relevantCount > 0 ? score / contexts.size() : 0.0;

        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("relevanceJudgments", relevanceJudgments);
            details.put("totalContexts", contexts.size());
            details.put("relevantContexts", relevantCount);
            return new MetricScore(score, objectMapper.writeValueAsString(details));
        } catch (Exception e) {
            return new MetricScore(score, "{}");
        }
    }
}
