package com.interview.agent.service.eval.metric;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Context Recall（检索召回）计算器
 * 将标准答案拆分为陈述，检查每个陈述是否能从检索上下文中推断出来
 */
@Slf4j
@Component
public class ContextRecallCalculator implements MetricCalculator {

    private static final String DECOMPOSE_PROMPT = "请从以下面试答案中提取所有独立的事实性陈述。以JSON数组格式输出，每个元素是一个陈述字符串。";

    private static final String VERIFY_SYSTEM_PROMPT = "你是一个答案完整性评估助手。根据提供的参考资料，判断该陈述是否可以被支持或推断出来。只回答 YES 或 NO，不要解释。";

    private final LlmJudgeClient judgeClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContextRecallCalculator(LlmJudgeClient judgeClient) {
        this.judgeClient = judgeClient;
    }

    @Override
    public String metricName() {
        return "context_recall";
    }

    @Override
    public MetricScore calculate(String question, String generatedAnswer,
                                  List<String> contexts, String groundTruth,
                                  List<String> groundTruthContexts) {
        if (groundTruth == null || groundTruth.isBlank()) {
            return new MetricScore(0.0, "{}");
        }

        // Step 1: 拆解标准答案为独立陈述
        List<String> statements = judgeClient.judgeJsonArray(DECOMPOSE_PROMPT, groundTruth);
        if (statements.isEmpty()) {
            return new MetricScore(0.0, "{\"error\":\"无法拆解标准答案\"}");
        }

        // Step 2: 对每个陈述，检查是否能从上下文推断
        String contextBlock = String.join("\n---\n", contexts);
        int supported = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (String statement : statements) {
            String userPrompt = "【检索到的参考资料】\n" + contextBlock + "\n\n【标准答案中的陈述】\n" + statement;
            boolean isSupported = judgeClient.judgeYesNo(VERIFY_SYSTEM_PROMPT, userPrompt);
            if (isSupported) supported++;

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("statement", statement);
            detail.put("supported", isSupported);
            details.add(detail);
        }

        double score = statements.isEmpty() ? 0.0 : (double) supported / statements.size();

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalStatements", statements.size());
            result.put("supportedStatements", supported);
            result.put("statements", details);
            return new MetricScore(score, objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            return new MetricScore(score, "{}");
        }
    }
}
