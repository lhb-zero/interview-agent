package com.interview.agent.service.eval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.common.exception.BusinessException;
import com.interview.agent.common.result.ResultCode;
import com.interview.agent.dao.mapper.EvalDatasetMapper;
import com.interview.agent.dao.mapper.EvalTestCaseMapper;
import com.interview.agent.model.dto.EvalDatasetImportDTO;
import com.interview.agent.model.entity.EvalDataset;
import com.interview.agent.model.entity.EvalTestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvalDatasetServiceImpl implements EvalDatasetService {

    private final EvalDatasetMapper datasetMapper;
    private final EvalTestCaseMapper testCaseMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public void importDataset(EvalDatasetImportDTO dto) {
        if (dto.getTestCases() == null || dto.getTestCases().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        // 创建数据集记录
        EvalDataset dataset = new EvalDataset();
        dataset.setName(dto.getName());
        dataset.setDescription(dto.getDescription());
        dataset.setDomain(dto.getDomain() != null ? dto.getDomain().toLowerCase() : "java");
        dataset.setTestCaseCount(dto.getTestCases().size());
        dataset.setStatus("ACTIVE");
        dataset.setCreatedAt(LocalDateTime.now());
        dataset.setUpdatedAt(LocalDateTime.now());
        datasetMapper.insert(dataset);

        // 逐条插入测试用例
        for (EvalDatasetImportDTO.TestCaseItem item : dto.getTestCases()) {
            EvalTestCase testCase = new EvalTestCase();
            testCase.setDatasetId(dataset.getId());
            testCase.setQuestion(item.getQuestion());
            testCase.setGroundTruthAnswer(item.getGroundTruthAnswer());
            testCase.setDifficulty(item.getDifficulty() != null ? item.getDifficulty() : "中级");
            testCase.setDomain(dataset.getDomain());
            testCase.setCreatedAt(LocalDateTime.now());

            // 将 groundTruthContexts 列表转为 JSON 字符串
            if (item.getGroundTruthContexts() != null) {
                try {
                    testCase.setGroundTruthContexts(objectMapper.writeValueAsString(item.getGroundTruthContexts()));
                } catch (Exception e) {
                    log.warn("序列化 groundTruthContexts 失败", e);
                    testCase.setGroundTruthContexts("[]");
                }
            } else {
                testCase.setGroundTruthContexts("[]");
            }

            testCaseMapper.insert(testCase);
        }

        log.info("数据集导入成功: name={}, domain={}, testCases={}", dataset.getName(), dataset.getDomain(), dataset.getTestCaseCount());
    }

    @Override
    public List<EvalDataset> listDatasets(String domain) {
        LambdaQueryWrapper<EvalDataset> wrapper = new LambdaQueryWrapper<>();
        if (domain != null && !domain.isEmpty()) {
            wrapper.eq(EvalDataset::getDomain, domain.toLowerCase());
        }
        wrapper.orderByDesc(EvalDataset::getCreatedAt);
        return datasetMapper.selectList(wrapper);
    }

    @Override
    public EvalDataset getDataset(Long datasetId) {
        EvalDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return dataset;
    }

    @Override
    public List<EvalTestCase> listTestCases(Long datasetId) {
        return testCaseMapper.selectList(
                new LambdaQueryWrapper<EvalTestCase>()
                        .eq(EvalTestCase::getDatasetId, datasetId)
                        .orderByAsc(EvalTestCase::getId)
        );
    }

    @Override
    @Transactional
    public void addTestCase(EvalTestCase testCase) {
        testCase.setCreatedAt(LocalDateTime.now());
        testCaseMapper.insert(testCase);

        // 更新数据集的用例计数
        EvalDataset dataset = datasetMapper.selectById(testCase.getDatasetId());
        if (dataset != null) {
            dataset.setTestCaseCount(dataset.getTestCaseCount() + 1);
            dataset.setUpdatedAt(LocalDateTime.now());
            datasetMapper.updateById(dataset);
        }
    }

    @Override
    @Transactional
    public void deleteDataset(Long datasetId) {
        datasetMapper.deleteById(datasetId);
        // 同时删除关联的测试用例
        testCaseMapper.delete(
                new LambdaQueryWrapper<EvalTestCase>()
                        .eq(EvalTestCase::getDatasetId, datasetId)
        );
        log.info("数据集已删除: id={}", datasetId);
    }
}
