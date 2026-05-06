package com.interview.agent.service.eval;

import com.interview.agent.model.dto.EvalExperimentRequestDTO;
import com.interview.agent.model.vo.EvalDashboardVO;
import com.interview.agent.model.vo.EvalExperimentVO;
import com.interview.agent.model.vo.EvalResultVO;

import java.util.List;

/**
 * 评估实验管理服务
 */
public interface EvalExperimentService {

    /**
     * 创建并运行评估实验
     */
    EvalExperimentVO createExperiment(EvalExperimentRequestDTO request);

    /**
     * 获取实验列表
     */
    List<EvalExperimentVO> listExperiments(Long datasetId);

    /**
     * 获取实验详情
     */
    EvalExperimentVO getExperiment(Long experimentId);

    /**
     * 获取实验的评估结果明细
     */
    List<EvalResultVO> listResults(Long experimentId);

    /**
     * 获取单条评估结果详情
     */
    EvalResultVO getResult(Long resultId);

    /**
     * 获取评估仪表盘汇总数据
     */
    EvalDashboardVO getDashboard();

    /**
     * 取消实验
     */
    void cancelExperiment(Long experimentId);

    /**
     * 删除实验
     */
    void deleteExperiment(Long experimentId);
}
