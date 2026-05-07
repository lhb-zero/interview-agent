package com.interview.agent.service.eval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.dao.mapper.EvalExperimentMapper;
import com.interview.agent.dao.mapper.EvalResultMapper;
import com.interview.agent.dao.mapper.EvalTestCaseMapper;
import com.interview.agent.model.entity.EvalExperiment;
import com.interview.agent.model.entity.EvalResult;
import com.interview.agent.model.entity.EvalTestCase;
import com.interview.agent.model.enums.EvalStatusEnum;
import com.interview.agent.service.eval.metric.MetricCalculator;
import com.interview.agent.service.eval.metric.MetricScore;
import com.interview.agent.service.rag.hybrid.HybridSearchProperties;
import com.interview.agent.service.rag.hybrid.HybridSearchService;
import com.interview.agent.service.rag.reranker.RerankingDocumentPostProcessor;
import com.interview.agent.service.rag.reranker.RerankerProperties;
import com.interview.agent.service.rag.rewrite.QueryRewriteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评估实验异步执行器（独立 Bean，解决 @Async 自调用不生效的问题）
 */
@Slf4j
@Component
public class EvalExperimentRunner {

    private final EvalExperimentMapper experimentMapper;
    private final EvalResultMapper resultMapper;
    private final EvalTestCaseMapper testCaseMapper;
    private final EvalProperties evalProperties;

    private final VectorStore vectorStore;
    private final QueryRewriteService queryRewriteService;
    private final HybridSearchService hybridSearchService;
    private final HybridSearchProperties hybridProperties;
    private final RerankingDocumentPostProcessor rerankingPostProcessor;
    private final RerankerProperties rerankerProperties;

    private final List<MetricCalculator> calculators;
    private final ChatModel chatModel;

    @Value("classpath:/prompts/rag-enhanced-answer.st")
    private Resource ragPromptResource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvalExperimentRunner(
            EvalExperimentMapper experimentMapper,
            EvalResultMapper resultMapper,
            EvalTestCaseMapper testCaseMapper,
            EvalProperties evalProperties,
            VectorStore vectorStore,
            QueryRewriteService queryRewriteService,
            HybridSearchService hybridSearchService,
            HybridSearchProperties hybridProperties,
            RerankingDocumentPostProcessor rerankingPostProcessor,
            RerankerProperties rerankerProperties,
            List<MetricCalculator> calculators,
            ChatModel chatModel) {
        this.experimentMapper = experimentMapper;
        this.resultMapper = resultMapper;
        this.testCaseMapper = testCaseMapper;
        this.evalProperties = evalProperties;
        this.vectorStore = vectorStore;
        this.queryRewriteService = queryRewriteService;
        this.hybridSearchService = hybridSearchService;
        this.hybridProperties = hybridProperties;
        this.rerankingPostProcessor = rerankingPostProcessor;
        this.rerankerProperties = rerankerProperties;
        this.calculators = calculators;
        this.chatModel = chatModel;
    }

    @Async
    public void runExperimentAsync(Long experimentId) {
        EvalExperiment experiment = experimentMapper.selectById(experimentId);
        if (experiment == null) return;

        experiment.setStatus(EvalStatusEnum.RUNNING.getCode());
        experiment.setStartedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        log.info("[Eval] 开始执行评估实验: id={}, name={}, totalCases={}",
                experimentId, experiment.getName(), experiment.getTotalCases());

        List<EvalTestCase> testCases = testCaseMapper.selectList(
                new LambdaQueryWrapper<EvalTestCase>()
                        .eq(EvalTestCase::getDatasetId, experiment.getDatasetId())
                        .last("LIMIT " + experiment.getTotalCases())
        );

        double totalPrecision = 0, totalRecall = 0, totalFaithfulness = 0, totalRelevancy = 0;
        int completed = 0, failed = 0;

        for (int i = 0; i < testCases.size(); i++) {
            EvalExperiment current = experimentMapper.selectById(experimentId);
            if (EvalStatusEnum.CANCELLED.getCode().equals(current.getStatus())) {
                log.info("[Eval] 实验已被取消: id={}", experimentId);
                return;
            }

            EvalTestCase testCase = testCases.get(i);
            EvalResult evalResult = new EvalResult();
            evalResult.setExperimentId(experimentId);
            evalResult.setTestCaseId(testCase.getId());
            evalResult.setCreatedAt(LocalDateTime.now());

            try {
                log.info("[Eval] 处理测试用例 {}/{}: {}", i + 1, testCases.size(),
                        testCase.getQuestion().substring(0, Math.min(50, testCase.getQuestion().length())));

                long retrievalStart = System.currentTimeMillis();
                List<Document> retrievedDocs = executeRetrieval(
                        testCase.getQuestion(),
                        testCase.getDomain(),
                        experiment.getQueryRewriteEnabled(),
                        experiment.getHybridSearchEnabled(),
                        experiment.getRerankerEnabled()
                );
                long retrievalTime = System.currentTimeMillis() - retrievalStart;
                evalResult.setRetrievalTimeMs((int) retrievalTime);

                List<String> contexts = retrievedDocs.stream()
                        .map(Document::getText)
                        .collect(Collectors.toList());
                evalResult.setRetrievedContexts(objectMapper.writeValueAsString(contexts));

                long genStart = System.currentTimeMillis();
                String generatedAnswer = generateAnswer(testCase.getQuestion(), contexts);
                long genTime = System.currentTimeMillis() - genStart;
                evalResult.setGenerationTimeMs((int) genTime);
                evalResult.setGeneratedAnswer(generatedAnswer);

                long evalStart = System.currentTimeMillis();
                List<String> gtContexts = parseGroundTruthContexts(testCase.getGroundTruthContexts());

                for (MetricCalculator calculator : calculators) {
                    if (!isMetricEnabled(calculator.metricName())) continue;
                    try {
                        MetricScore score = calculator.calculate(
                                testCase.getQuestion(),
                                generatedAnswer,
                                contexts,
                                testCase.getGroundTruthAnswer(),
                                gtContexts
                        );
                        setMetricScore(evalResult, calculator.metricName(), score);
                    } catch (Exception e) {
                        log.warn("[Eval] 指标 {} 计算失败: {}", calculator.metricName(), e.getMessage());
                    }
                }

                long evalTime = System.currentTimeMillis() - evalStart;
                evalResult.setEvalTimeMs((int) evalTime);
                evalResult.setStatus("COMPLETED");
                completed++;

                totalPrecision += evalResult.getContextPrecision() != null ? evalResult.getContextPrecision() : 0;
                totalRecall += evalResult.getContextRecall() != null ? evalResult.getContextRecall() : 0;
                totalFaithfulness += evalResult.getFaithfulness() != null ? evalResult.getFaithfulness() : 0;
                totalRelevancy += evalResult.getAnswerRelevancy() != null ? evalResult.getAnswerRelevancy() : 0;

            } catch (Exception e) {
                log.error("[Eval] 测试用例处理失败: {}", testCase.getQuestion(), e);
                evalResult.setStatus("FAILED");
                evalResult.setErrorMessage(e.getMessage());
                failed++;
            }

            resultMapper.insert(evalResult);

            experiment.setCompletedCases(completed);
            experiment.setFailedCases(failed);
            experimentMapper.updateById(experiment);
        }

        int total = completed + failed;
        if (completed > 0) {
            experiment.setAvgContextPrecision(round(totalPrecision / completed));
            experiment.setAvgContextRecall(round(totalRecall / completed));
            experiment.setAvgFaithfulness(round(totalFaithfulness / completed));
            experiment.setAvgAnswerRelevancy(round(totalRelevancy / completed));
            experiment.setOverallScore(round(
                    (totalPrecision + totalRecall + totalFaithfulness + totalRelevancy) / (4 * completed)
            ));
        }
        experiment.setStatus(EvalStatusEnum.COMPLETED.getCode());
        experiment.setCompletedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        log.info("[Eval] 实验完成: id={}, completed={}, failed={}, overallScore={}",
                experimentId, completed, failed, experiment.getOverallScore());
    }

    private List<Document> executeRetrieval(String question, String domain,
                                             boolean queryRewrite, boolean hybridSearch, boolean reranker) {
        String searchQuery = question;
        if (queryRewrite) {
            try {
                searchQuery = queryRewriteService.rewriteIfNeeded(question);
            } catch (Exception e) {
                log.warn("[Eval] 查询改写失败，使用原始查询: {}", e.getMessage());
            }
        }

        int candidateCount = reranker ? rerankerProperties.getCandidateCount() : 5;

        List<Document> results;
        if (hybridSearch && hybridProperties.isEnabled()) {
            results = hybridSearchService.hybridSearch(searchQuery, domain, candidateCount);
        } else {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(searchQuery)
                    .topK(candidateCount)
                    .similarityThreshold(0.5);
            if (domain != null && !domain.isEmpty()) {
                builder.filterExpression("domain == '" + domain.toLowerCase() + "'");
            }
            results = vectorStore.similaritySearch(builder.build());
        }

        if (reranker && rerankerProperties.isEnabled() && !results.isEmpty()) {
            try {
                results = rerankingPostProcessor.rerank(question, results);
            } catch (Exception e) {
                log.warn("[Eval] Reranking 失败，使用原始结果: {}", e.getMessage());
            }
        }

        return results;
    }

    private String generateAnswer(String question, List<String> contexts) {
        String context;
        if (contexts.isEmpty()) {
            context = "[无相关参考资料]";
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < contexts.size(); i++) {
                sb.append("【文档").append(i + 1).append("】\n");
                sb.append(contexts.get(i));
                if (i < contexts.size() - 1) sb.append("\n\n");
            }
            context = sb.toString();
        }

        String templateContent;
        try {
            templateContent = ragPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("读取 RAG 模板失败", e);
        }

        PromptTemplate template = new PromptTemplate(templateContent);
        String systemContent = template.render(Map.of("context", context, "question", question));

        OllamaChatOptions options = OllamaChatOptions.builder()
                .disableThinking()
                .build();
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemContent),
                new UserMessage(question)
        ), options);

        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    private boolean isMetricEnabled(String metricName) {
        EvalProperties.Metrics m = evalProperties.getMetrics();
        return switch (metricName) {
            case "context_precision" -> m.isContextPrecisionEnabled();
            case "context_recall" -> m.isContextRecallEnabled();
            case "faithfulness" -> m.isFaithfulnessEnabled();
            case "answer_relevancy" -> m.isAnswerRelevancyEnabled();
            default -> true;
        };
    }

    private void setMetricScore(EvalResult result, String metricName, MetricScore score) {
        switch (metricName) {
            case "context_precision" -> {
                result.setContextPrecision(score.getScore());
                result.setPrecisionDetails(score.getDetailsJson());
            }
            case "context_recall" -> {
                result.setContextRecall(score.getScore());
                result.setRecallDetails(score.getDetailsJson());
            }
            case "faithfulness" -> {
                result.setFaithfulness(score.getScore());
                result.setFaithfulnessDetails(score.getDetailsJson());
            }
            case "answer_relevancy" -> {
                result.setAnswerRelevancy(score.getScore());
                result.setRelevancyDetails(score.getDetailsJson());
            }
        }
    }

    private List<String> parseGroundTruthContexts(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
