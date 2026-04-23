package com.interview.agent.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识库文档状态枚举
 */
@Getter
@AllArgsConstructor
public enum DocumentStatusEnum {

    ACTIVE("ACTIVE", "有效"),
    PROCESSING("PROCESSING", "处理中"),
    DELETED("DELETED", "已删除");

    private final String code;
    private final String description;
}
