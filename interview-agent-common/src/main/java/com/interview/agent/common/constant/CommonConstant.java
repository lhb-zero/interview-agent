package com.interview.agent.common.constant;

/**
 * 项目常量定义
 */
public class CommonConstant {

    private CommonConstant() {}

    // ==================== 通用常量 ====================

    /** 默认分页大小 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 最大分页大小 */
    public static final int MAX_PAGE_SIZE = 100;

    // ==================== AI 相关常量 ====================

    /** 默认 Chat 模型 */
    public static final String DEFAULT_CHAT_MODEL = "qwen3.5:4b";

    /** 默认 Embedding 模型 */
    public static final String DEFAULT_EMBEDDING_MODEL = "bge-m3";

    /** Embedding 向量维度 */
    public static final int EMBEDDING_DIMENSION = 1024;

    /** RAG 检索默认 Top-K */
    public static final int RAG_DEFAULT_TOP_K = 5;

    /** RAG 相似度阈值（余弦相似度，范围 -1~1，低于此值的检索结果不参与增强） */
    public static final double RAG_SIMILARITY_THRESHOLD = 0.5;

    /** 文本分块默认大小（Token） */
    public static final int CHUNK_DEFAULT_SIZE = 500;

    /** 文本分块默认重叠大小（Token） */
    public static final int CHUNK_DEFAULT_OVERLAP = 50;

    // ==================== 领域常量 ====================

    /** Java 领域 */
    public static final String DOMAIN_JAVA = "java";

    /** Python 领域 */
    public static final String DOMAIN_PYTHON = "python";

    /** AI 领域 */
    public static final String DOMAIN_AI = "ai";

    // ==================== 对话角色 ====================

    /** 系统角色 */
    public static final String ROLE_SYSTEM = "system";

    /** 用户角色 */
    public static final String ROLE_USER = "user";

    /** 助手角色 */
    public static final String ROLE_ASSISTANT = "assistant";
}
