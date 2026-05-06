package com.interview.agent.service.eval.metric;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 指标计算结果
 */
@Data
@AllArgsConstructor
public class MetricScore {

    /** 得分 (0.0 ~ 1.0) */
    private double score;

    /** 调试详情（JSON格式） */
    private String detailsJson;
}
