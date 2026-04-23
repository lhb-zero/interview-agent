package com.interview.agent.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面试难度枚举
 */
@Getter
@AllArgsConstructor
public enum DifficultyEnum {

    BASIC("基础", "基础面试题，考察基本概念和用法"),
    MEDIUM("中级", "中级面试题，考察原理和项目实践"),
    ADVANCED("高级", "高级面试题，考察架构设计和源码理解");

    private final String label;
    private final String description;
}
