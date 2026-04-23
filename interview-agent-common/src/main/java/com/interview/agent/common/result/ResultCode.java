package com.interview.agent.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),

    // 参数校验 4xx
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),

    // 业务错误 5xx
    MODEL_NOT_AVAILABLE(5001, "模型不可用"),
    DOCUMENT_PARSE_ERROR(5002, "文档解析失败"),
    EMBEDDING_ERROR(5003, "向量生成失败"),
    RAG_SEARCH_ERROR(5004, "RAG检索失败"),
    CHAT_ERROR(5005, "对话生成失败"),
    VECTOR_STORE_ERROR(5006, "向量存储失败");

    private final int code;
    private final String message;
}
