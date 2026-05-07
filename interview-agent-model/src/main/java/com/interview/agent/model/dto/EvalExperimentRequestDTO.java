package com.interview.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建评估实验请求 DTO
 */
@Data
public class EvalExperimentRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 实验名称 */
    @NotBlank(message = "实验名称不能为空")
    private String name;

    /** 数据集ID */
    @NotNull(message = "数据集ID不能为空")
    private Long datasetId;

    /** 是否启用查询改写 */
    private Boolean queryRewriteEnabled = true;

    /** 是否启用混合检索 */
    private Boolean hybridSearchEnabled = true;

    /** 是否启用Reranker */
    private Boolean rerankerEnabled = true;

    /** 最大测试用例数（null 或 0 表示全部运行） */
    private Integer maxCases;
}
