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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final EvalExperimentRunner experimentRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvalExperimentServiceImpl(
            EvalExperimentMapper experimentMapper,
            EvalResultMapper resultMapper,
            EvalDatasetMapper datasetMapper,
            EvalTestCaseMapper testCaseMapper,
            EvalExperimentRunner experimentRunner) {
        this.experimentMapper = experimentMapper;
        this.resultMapper = resultMapper;
        this.datasetMapper = datasetMapper;
        this.testCaseMapper = testCaseMapper;
        this.experimentRunner = experimentRunner;
    }

    @Override
    public EvalExperimentVO createExperiment(EvalExperimentRequestDTO request) {
        EvalDataset dataset = datasetMapper.selectById(request.getDatasetId());
        if (dataset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        int totalCases = dataset.getTestCaseCount();
        if (request.getMaxCases() != null && request.getMaxCases() > 0 && request.getMaxCases() < totalCases) {
            totalCases = request.getMaxCases();
        }

        EvalExperiment experiment = new EvalExperiment();
        experiment.setName(request.getName());
        experiment.setDatasetId(request.getDatasetId());
        experiment.setQueryRewriteEnabled(request.getQueryRewriteEnabled());
        experiment.setHybridSearchEnabled(request.getHybridSearchEnabled());
        experiment.setRerankerEnabled(request.getRerankerEnabled());
        experiment.setTotalCases(totalCases);
        experiment.setCompletedCases(0);
        experiment.setFailedCases(0);
        experiment.setStatus(EvalStatusEnum.PENDING.getCode());
        experiment.setCreatedAt(LocalDateTime.now());
        experimentMapper.insert(experiment);

        // 通过独立 Bean 调用 @Async，解决自调用不生效的问题
        experimentRunner.runExperimentAsync(experiment.getId());

        return toVO(experiment, dataset.getName());
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

        Long totalCases = testCaseMapper.selectCount(null);
        dashboard.setTotalTestCases(totalCases != null ? totalCases.intValue() : 0);

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

        vo.setRetrievedContexts(parseJsonList(r.getRetrievedContexts()));
        vo.setPrecisionDetails(parseJsonMap(r.getPrecisionDetails()));
        vo.setRecallDetails(parseJsonMap(r.getRecallDetails()));
        vo.setFaithfulnessDetails(parseJsonMap(r.getFaithfulnessDetails()));
        vo.setRelevancyDetails(parseJsonMap(r.getRelevancyDetails()));

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
