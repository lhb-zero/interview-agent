package com.interview.agent.service.eval.metric;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Faithfulness（忠实度）计算器
 * 从生成回答中提取事实性声明，验证每个声明是否有上下文支持
 */
@Slf4j
@Component
public class FaithfulnessCalculator implements MetricCalculator {

    private static final String EXTRACT_PROMPT = "请从以下面试回答中提取所有事实性陈述（claims）。每个陈述应该是独立的、可验证的事实。以JSON数组格式输出，每个元素是一个陈述字符串。";

    private static final String VERIFY_SYSTEM_PROMPT = "你是一个事实核查助手。判断以下陈述是否被参考资料所支持。只回答 SUPPORTED 或 NOT_SUPPORTED，不要解释。";

    private final LlmJudgeClient judgeClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FaithfulnessCalculator(LlmJudgeClient judgeClient) {
        this.judgeClient = judgeClient;
    }

    @Override
    public String metricName() {
        return "faithfulness";
    }

    @Override
    public MetricScore calculate(String question, String generatedAnswer,
                                  List<String> contexts, String groundTruth,
                                  List<String> groundTruthContexts) {
        if (generatedAnswer == null || generatedAnswer.isBlank()) {
            return new MetricScore(0.0, "{}");
        }

        // Step 1: 从生成回答中提取事实性声明
        List<String> claims = judgeClient.judgeJsonArray(EXTRACT_PROMPT, generatedAnswer);
        if (claims.isEmpty()) {
            return new MetricScore(1.0, "{\"note\":\"无可验证声明\"}");
        }

        // Step 2: 验证每个声明是否有上下文支持
        String contextBlock = String.join("\n---\n", contexts);
        int supported = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (String claim : claims) {
            String userPrompt = "【参考资料】\n" + contextBlock + "\n\n【待验证陈述】\n" + claim;
            boolean isSupported = judgeClient.judgeYesNo(VERIFY_SYSTEM_PROMPT, userPrompt);
            if (isSupported) supported++;

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("claim", claim);
            detail.put("supported", isSupported);
            details.add(detail);
        }

        double score = claims.isEmpty() ? 1.0 : (double) supported / claims.size();

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalClaims", claims.size());
            result.put("supportedClaims", supported);
            result.put("claims", details);
            return new MetricScore(score, objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            return new MetricScore(score, "{}");
        }
    }
}
