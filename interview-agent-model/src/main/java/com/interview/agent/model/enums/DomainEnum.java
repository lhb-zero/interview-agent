package com.interview.agent.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 技术领域枚举
 */
@Getter
@AllArgsConstructor
public enum DomainEnum {

    JAVA("java", "Java"),
    PYTHON("python", "Python"),
    AI("ai", "AI/机器学习"),
    FRONTEND("frontend", "前端"),
    DATABASE("database", "数据库"),
    SYSTEM("system", "系统设计");

    private final String code;
    private final String label;
}
