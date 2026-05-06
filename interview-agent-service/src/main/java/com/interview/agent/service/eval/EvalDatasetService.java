package com.interview.agent.service.eval;

import com.interview.agent.model.dto.EvalDatasetImportDTO;
import com.interview.agent.model.entity.EvalDataset;
import com.interview.agent.model.entity.EvalTestCase;

import java.util.List;

/**
 * 评估数据集管理服务
 */
public interface EvalDatasetService {

    /**
     * 导入数据集
     */
    void importDataset(EvalDatasetImportDTO dto);

    /**
     * 获取数据集列表
     */
    List<EvalDataset> listDatasets(String domain);

    /**
     * 获取数据集详情
     */
    EvalDataset getDataset(Long datasetId);

    /**
     * 获取数据集下的测试用例
     */
    List<EvalTestCase> listTestCases(Long datasetId);

    /**
     * 添加单条测试用例
     */
    void addTestCase(EvalTestCase testCase);

    /**
     * 删除数据集
     */
    void deleteDataset(Long datasetId);
}
