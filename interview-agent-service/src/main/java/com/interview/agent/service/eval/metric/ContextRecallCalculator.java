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

    private static final int MAX_STATEMENTS = 8;

    private static final String DECOMPOSE_PROMPT = "请从以下面试答案中提取最多%d个核心事实性陈述。"
            + "只提取关键事实，不要过度拆分。以JSON数组格式输出，每个元素是一个陈述字符串。不要添加任何其他文字。";

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

        // Step 1: 拆解标准答案为独立陈述（限制数量避免 prompt 过大）
        List<String> statements = judgeClient.judgeJsonArray(
                String.format(DECOMPOSE_PROMPT, MAX_STATEMENTS), groundTruth);
        if (statements.size() > MAX_STATEMENTS) {
            log.info("[ContextRecall] 陈述数过多({})，截断至{}", statements.size(), MAX_STATEMENTS);
            statements = statements.subList(0, MAX_STATEMENTS);
        }
        if (statements.isEmpty()) {
            return new MetricScore(0.0, "{\"error\":\"无法拆解标准答案\"}");
        }

        // Step 2: 批量验证每个陈述是否能从上下文推断
        String contextBlock = String.join("\n---\n", contexts);
        List<String> items = new ArrayList<>();
        for (String statement : statements) {
            items.add("【检索到的参考资料】\n" + contextBlock + "\n\n【标准答案中的陈述】\n" + statement);
        }
        List<Boolean> supportedList = judgeClient.judgeYesNoBatch(VERIFY_SYSTEM_PROMPT, items, "陈述");

        int supported = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (int i = 0; i < statements.size(); i++) {
            boolean isSupported = supportedList.get(i);
            if (isSupported) supported++;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("statement", statements.get(i));
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
