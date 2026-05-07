package com.interview.agent.service.eval.metric;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.service.eval.EvalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Answer Relevancy（答案相关性）计算器
 * 让 LLM 从回答反推生成 N 个可能的问题，用 Embedding 计算与原始问题的语义相似度
 */
@Slf4j
@Component
public class AnswerRelevancyCalculator implements MetricCalculator {

    private static final String GEN_PROMPT_TEMPLATE = "请根据以下面试回答，生成%d个可能引出此回答的面试问题。问题应该多样化，从不同角度提问。\n\n严格要求：只输出一个JSON字符串数组，不要添加任何其他字段或格式。\n示例输出格式：[\"问题1\", \"问题2\", \"问题3\"]";

    private final LlmJudgeClient judgeClient;
    private final EmbeddingModel embeddingModel;
    private final EvalProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnswerRelevancyCalculator(LlmJudgeClient judgeClient,
                                      EmbeddingModel embeddingModel,
                                      EvalProperties properties) {
        this.judgeClient = judgeClient;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public String metricName() {
        return "answer_relevancy";
    }

    @Override
    public MetricScore calculate(String question, String generatedAnswer,
                                  List<String> contexts, String groundTruth,
                                  List<String> groundTruthContexts) {
        if (generatedAnswer == null || generatedAnswer.isBlank()) {
            return new MetricScore(0.0, "{}");
        }

        int n = properties.getMetrics().getRelevancySyntheticQuestions();

        // Step 1: 让 LLM 从回答反推生成 N 个可能的问题
        String systemPrompt = String.format(GEN_PROMPT_TEMPLATE, n);
        List<String> syntheticQuestions = judgeClient.judgeJsonArray(systemPrompt, generatedAnswer);
        if (syntheticQuestions.isEmpty()) {
            return new MetricScore(0.0, "{\"error\":\"无法生成反推问题\"}");
        }

        // Step 2: 用 Embedding 计算余弦相似度
        float[] originalEmbedding = embeddingModel.embed(question);
        double totalSimilarity = 0.0;
        List<Double> similarities = new ArrayList<>();

        for (String sq : syntheticQuestions) {
            float[] sqEmbedding = embeddingModel.embed(sq);
            double cosine = cosineSimilarity(originalEmbedding, sqEmbedding);
            similarities.add(cosine);
            totalSimilarity += cosine;
        }

        double score = totalSimilarity / syntheticQuestions.size();

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("originalQuestion", question);
            result.put("syntheticQuestions", syntheticQuestions);
            result.put("similarities", similarities);
            result.put("averageSimilarity", score);
            return new MetricScore(score, objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            return new MetricScore(score, "{}");
        }
    }

    /**
     * 计算两个向量的余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
