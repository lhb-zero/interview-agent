package com.interview.agent.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评估实验状态枚举
 */
@Getter
@AllArgsConstructor
public enum EvalStatusEnum {

    PENDING("PENDING", "待执行"),
    RUNNING("RUNNING", "运行中"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "失败"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;
}
