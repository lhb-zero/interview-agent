package com.interview.agent.service.eval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.common.exception.BusinessException;
import com.interview.agent.common.result.ResultCode;
import com.interview.agent.dao.mapper.EvalDatasetMapper;
import com.interview.agent.dao.mapper.EvalExperimentMapper;
import com.interview.agent.dao.mapper.EvalResultMapper;
import com.interview.agent.dao.mapper.EvalTestCaseMapper;
import com.interview.agent.model.dto.EvalExperimentRequestDTO;
import com.interview.agent.model.entity.EvalDataset;
import com.interview.agent.model.entity.EvalExperiment;
import com.interview.agent.model.entity.EvalResult;
import com.interview.agent.model.entity.EvalTestCase;
import com.interview.agent.model.enums.EvalStatusEnum;
import com.interview.agent.model.vo.EvalDashboardVO;
import com.interview.agent.model.vo.EvalExperimentVO;
import com.interview.agent.model.vo.EvalResultVO;
import com.interview.agent.service.eval.metric.MetricCalculator;
import com.interview.agent.service.eval.metric.MetricScore;
import com.interview.agent.service.rag.hybrid.HybridSearchProperties;
import com.interview.agent.service.rag.hybrid.HybridSearchService;
import com.interview.agent.service.rag.reranker.RerankingDocumentPostProcessor;
import com.interview.agent.service.rag.reranker.RerankerProperties;
import com.interview.agent.service.rag.rewrite.QueryRewriteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
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
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EvalExperimentServiceImpl implements EvalExperimentService {

    private final EvalExperimentMapper experimentMapper;
    private final EvalResultMapper resultMapper;
    private final EvalDatasetMapper datasetMapper;
    private final EvalTestCaseMapper testCaseMapper;
    private final EvalProperties evalProperties;

    // RAG pipeline 组件（复用 RagServiceImpl 的检索流程）
    private final VectorStore vectorStore;
    private final QueryRewriteService queryRewriteService;
    private final HybridSearchService hybridSearchService;
    private final HybridSearchProperties hybridProperties;
    private final RerankingDocumentPostProcessor rerankingPostProcessor;
    private final RerankerProperties rerankerProperties;

    // 评估指标计算器
    private final List<MetricCalculator> calculators;

    // LLM 用于生成回答
    private final ChatModel chatModel;

    @Value("classpath:/prompts/rag-enhanced-answer.st")
    private Resource ragPromptResource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvalExperimentServiceImpl(
            EvalExperimentMapper experimentMapper,
            EvalResultMapper resultMapper,
            EvalDatasetMapper datasetMapper,
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
        this.datasetMapper = datasetMapper;
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

    @Override
    public EvalExperimentVO createExperiment(EvalExperimentRequestDTO request) {
        // 验证数据集存在
        EvalDataset dataset = datasetMapper.selectById(request.getDatasetId());
        if (dataset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        // 创建实验记录
        EvalExperiment experiment = new EvalExperiment();
        experiment.setName(request.getName());
        experiment.setDatasetId(request.getDatasetId());
        experiment.setQueryRewriteEnabled(request.getQueryRewriteEnabled());
        experiment.setHybridSearchEnabled(request.getHybridSearchEnabled());
        experiment.setRerankerEnabled(request.getRerankerEnabled());
        experiment.setTotalCases(dataset.getTestCaseCount());
        experiment.setCompletedCases(0);
        experiment.setFailedCases(0);
        experiment.setStatus(EvalStatusEnum.PENDING.getCode());
        experiment.setCreatedAt(LocalDateTime.now());
        experimentMapper.insert(experiment);

        // 异步执行评估
        runExperimentAsync(experiment.getId());

        return toVO(experiment, dataset.getName());
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

        // 加载测试用例
        List<EvalTestCase> testCases = testCaseMapper.selectList(
                new LambdaQueryWrapper<EvalTestCase>()
                        .eq(EvalTestCase::getDatasetId, experiment.getDatasetId())
        );

        double totalPrecision = 0, totalRecall = 0, totalFaithfulness = 0, totalRelevancy = 0;
        int completed = 0, failed = 0;

        for (int i = 0; i < testCases.size(); i++) {
            // 检查是否被取消
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

                // Step 1: 执行 RAG 检索
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

                // 保存检索到的上下文
                List<String> contexts = retrievedDocs.stream()
                        .map(Document::getText)
                        .collect(Collectors.toList());
                evalResult.setRetrievedContexts(objectMapper.writeValueAsString(contexts));

                // Step 2: 用 RAG 生成回答
                long genStart = System.currentTimeMillis();
                String generatedAnswer = generateAnswer(testCase.getQuestion(), contexts);
                long genTime = System.currentTimeMillis() - genStart;
                evalResult.setGenerationTimeMs((int) genTime);
                evalResult.setGeneratedAnswer(generatedAnswer);

                // Step 3: 计算评估指标
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

                // 累加指标
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

            // 更新实验进度
            experiment.setCompletedCases(completed);
            experiment.setFailedCases(failed);
            experimentMapper.updateById(experiment);
        }

        // 计算聚合指标
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

    /**
     * 执行 RAG 检索流程（复用 RagServiceImpl 的逻辑）
     */
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

        // Reranking
        if (reranker && rerankerProperties.isEnabled() && !results.isEmpty()) {
            try {
                results = rerankingPostProcessor.rerank(question, results);
            } catch (Exception e) {
                log.warn("[Eval] Reranking 失败，使用原始结果: {}", e.getMessage());
            }
        }

        return results;
    }

    /**
     * 用 LLM 生成 RAG 回答
     */
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

    // ==================== 查询方法 ====================

    @Override
    public List<EvalExperimentVO> listExperiments(Long datasetId) {
        LambdaQueryWrapper<EvalExperiment> wrapper = new LambdaQueryWrapper<>();
        if (datasetId != null) {
            wrapper.eq(EvalExperiment::getDatasetId, datasetId);
        }
        wrapper.orderByDesc(EvalExperiment::getCreatedAt);
        List<EvalExperiment> experiments = experimentMapper.selectList(wrapper);
        return experiments.stream().map(e -> {
            String datasetName = getDatasetName(e.getDatasetId());
            return toVO(e, datasetName);
        }).collect(Collectors.toList());
    }

    @Override
    public EvalExperimentVO getExperiment(Long experimentId) {
        EvalExperiment experiment = experimentMapper.selectById(experimentId);
        if (experiment == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return toVO(experiment, getDatasetName(experiment.getDatasetId()));
    }

    @Override
    public List<EvalResultVO> listResults(Long experimentId) {
        List<EvalResult> results = resultMapper.selectList(
                new LambdaQueryWrapper<EvalResult>()
                        .eq(EvalResult::getExperimentId, experimentId)
                        .orderByAsc(EvalResult::getId)
        );
        return results.stream().map(this::toResultVO).collect(Collectors.toList());
    }

    @Override
    public EvalResultVO getResult(Long resultId) {
        EvalResult result = resultMapper.selectById(resultId);
        if (result == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return toResultVO(result);
    }

    @Override
    public EvalDashboardVO getDashboard() {
        EvalDashboardVO dashboard = new EvalDashboardVO();
        dashboard.setTotalDatasets(Math.toIntExact(datasetMapper.selectCount(null)));
        dashboard.setTotalExperiments(Math.toIntExact(experimentMapper.selectCount(null)));

        // 统计测试用例总数
        Long totalCases = testCaseMapper.selectCount(null);
        dashboard.setTotalTestCases(totalCases != null ? totalCases.intValue() : 0);

        // 最近 5 个实验
        List<EvalExperiment> recent = experimentMapper.selectList(
                new LambdaQueryWrapper<EvalExperiment>()
                        .orderByDesc(EvalExperiment::getCreatedAt)
                        .last("LIMIT 5")
        );
        dashboard.setRecentExperiments(recent.stream().map(e -> {
            EvalDashboardVO.ExperimentSummary summary = new EvalDashboardVO.ExperimentSummary();
            summary.setId(e.getId());
            summary.setName(e.getName());
            summary.setStatus(e.getStatus());
            summary.setOverallScore(e.getOverallScore());
            summary.setCreatedAt(e.getCreatedAt());
            return summary;
        }).collect(Collectors.toList()));

        return dashboard;
    }

    @Override
    public void cancelExperiment(Long experimentId) {
        EvalExperiment experiment = experimentMapper.selectById(experimentId);
        if (experiment == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!EvalStatusEnum.RUNNING.getCode().equals(experiment.getStatus())
                && !EvalStatusEnum.PENDING.getCode().equals(experiment.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        experiment.setStatus(EvalStatusEnum.CANCELLED.getCode());
        experimentMapper.updateById(experiment);
    }

    @Override
    public void deleteExperiment(Long experimentId) {
        experimentMapper.deleteById(experimentId);
        resultMapper.delete(
                new LambdaQueryWrapper<EvalResult>()
                        .eq(EvalResult::getExperimentId, experimentId)
        );
    }

    private String getDatasetName(Long datasetId) {
        EvalDataset dataset = datasetMapper.selectById(datasetId);
        return dataset != null ? dataset.getName() : "未知数据集";
    }

    private EvalExperimentVO toVO(EvalExperiment e, String datasetName) {
        EvalExperimentVO vo = new EvalExperimentVO();
        vo.setId(e.getId());
        vo.setName(e.getName());
        vo.setDatasetId(e.getDatasetId());
        vo.setDatasetName(datasetName);
        vo.setQueryRewriteEnabled(e.getQueryRewriteEnabled());
        vo.setHybridSearchEnabled(e.getHybridSearchEnabled());
        vo.setRerankerEnabled(e.getRerankerEnabled());
        vo.setAvgContextPrecision(e.getAvgContextPrecision());
        vo.setAvgContextRecall(e.getAvgContextRecall());
        vo.setAvgFaithfulness(e.getAvgFaithfulness());
        vo.setAvgAnswerRelevancy(e.getAvgAnswerRelevancy());
        vo.setOverallScore(e.getOverallScore());
        vo.setTotalCases(e.getTotalCases());
        vo.setCompletedCases(e.getCompletedCases());
        vo.setFailedCases(e.getFailedCases());
        vo.setStatus(e.getStatus());
        vo.setErrorMessage(e.getErrorMessage());
        vo.setStartedAt(e.getStartedAt());
        vo.setCompletedAt(e.getCompletedAt());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    private EvalResultVO toResultVO(EvalResult r) {
        EvalResultVO vo = new EvalResultVO();
        vo.setId(r.getId());
        vo.setExperimentId(r.getExperimentId());
        vo.setTestCaseId(r.getTestCaseId());
        vo.setGeneratedAnswer(r.getGeneratedAnswer());
        vo.setRewrittenQuery(r.getRewrittenQuery());
        vo.setContextPrecision(r.getContextPrecision());
        vo.setContextRecall(r.getContextRecall());
        vo.setFaithfulness(r.getFaithfulness());
        vo.setAnswerRelevancy(r.getAnswerRelevancy());
        vo.setRetrievalTimeMs(r.getRetrievalTimeMs());
        vo.setGenerationTimeMs(r.getGenerationTimeMs());
        vo.setEvalTimeMs(r.getEvalTimeMs());
        vo.setStatus(r.getStatus());
        vo.setErrorMessage(r.getErrorMessage());

        // 解析 JSON 字段
        vo.setRetrievedContexts(parseJsonList(r.getRetrievedContexts()));
        vo.setPrecisionDetails(parseJsonMap(r.getPrecisionDetails()));
        vo.setRecallDetails(parseJsonMap(r.getRecallDetails()));
        vo.setFaithfulnessDetails(parseJsonMap(r.getFaithfulnessDetails()));
        vo.setRelevancyDetails(parseJsonMap(r.getRelevancyDetails()));

        // 关联测试用例信息
        EvalTestCase tc = testCaseMapper.selectById(r.getTestCaseId());
        if (tc != null) {
            vo.setQuestion(tc.getQuestion());
            vo.setGroundTruthAnswer(tc.getGroundTruthAnswer());
        }

        return vo;
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
