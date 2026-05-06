package com.interview.agent.web.controller;

import com.interview.agent.common.result.Result;
import com.interview.agent.model.dto.EvalDatasetImportDTO;
import com.interview.agent.model.dto.EvalExperimentRequestDTO;
import com.interview.agent.model.entity.EvalDataset;
import com.interview.agent.model.entity.EvalTestCase;
import com.interview.agent.model.vo.EvalDashboardVO;
import com.interview.agent.model.vo.EvalExperimentVO;
import com.interview.agent.model.vo.EvalResultVO;
import com.interview.agent.service.eval.EvalDatasetService;
import com.interview.agent.service.eval.EvalExperimentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RAG 评估管理接口
 */
@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvalController {

    private final EvalDatasetService datasetService;
    private final EvalExperimentService experimentService;

    // ==================== 数据集管理 ====================

    @PostMapping("/datasets/import")
    public Result<Void> importDataset(@RequestBody EvalDatasetImportDTO dto) {
        datasetService.importDataset(dto);
        return Result.success();
    }

    @GetMapping("/datasets")
    public Result<List<EvalDataset>> listDatasets(@RequestParam(required = false) String domain) {
        return Result.success(datasetService.listDatasets(domain));
    }

    @GetMapping("/datasets/{id}")
    public Result<EvalDataset> getDataset(@PathVariable Long id) {
        return Result.success(datasetService.getDataset(id));
    }

    @GetMapping("/datasets/{id}/cases")
    public Result<List<EvalTestCase>> listTestCases(@PathVariable Long id) {
        return Result.success(datasetService.listTestCases(id));
    }

    @PostMapping("/datasets/{id}/cases")
    public Result<Void> addTestCase(@PathVariable Long id, @RequestBody EvalTestCase testCase) {
        testCase.setDatasetId(id);
        datasetService.addTestCase(testCase);
        return Result.success();
    }

    @DeleteMapping("/datasets/{id}")
    public Result<Void> deleteDataset(@PathVariable Long id) {
        datasetService.deleteDataset(id);
        return Result.success();
    }

    // ==================== 实验管理 ====================

    @PostMapping("/experiments")
    public Result<EvalExperimentVO> createExperiment(@Valid @RequestBody EvalExperimentRequestDTO request) {
        return Result.success(experimentService.createExperiment(request));
    }

    @GetMapping("/experiments")
    public Result<List<EvalExperimentVO>> listExperiments(@RequestParam(required = false) Long datasetId) {
        return Result.success(experimentService.listExperiments(datasetId));
    }

    @GetMapping("/experiments/{id}")
    public Result<EvalExperimentVO> getExperiment(@PathVariable Long id) {
        return Result.success(experimentService.getExperiment(id));
    }

    @GetMapping("/experiments/{id}/results")
    public Result<List<EvalResultVO>> listResults(@PathVariable Long id) {
        return Result.success(experimentService.listResults(id));
    }

    @GetMapping("/results/{id}")
    public Result<EvalResultVO> getResult(@PathVariable Long id) {
        return Result.success(experimentService.getResult(id));
    }

    @PostMapping("/experiments/{id}/cancel")
    public Result<Void> cancelExperiment(@PathVariable Long id) {
        experimentService.cancelExperiment(id);
        return Result.success();
    }

    @DeleteMapping("/experiments/{id}")
    public Result<Void> deleteExperiment(@PathVariable Long id) {
        experimentService.deleteExperiment(id);
        return Result.success();
    }

    // ==================== 仪表盘 ====================

    @GetMapping("/dashboard")
    public Result<EvalDashboardVO> getDashboard() {
        return Result.success(experimentService.getDashboard());
    }
}
