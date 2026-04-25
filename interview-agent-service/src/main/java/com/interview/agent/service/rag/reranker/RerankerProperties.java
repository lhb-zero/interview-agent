package com.interview.agent.service.rag.reranker;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Reranker 配置属性
 *
 * 面试知识点：Spring Boot @ConfigurationProperties 最佳实践
 * - 将 application.yml 中 app.reranker.* 前缀的配置映射到 Java 对象
 * - 比 @Value 逐个注入更清晰、更易维护
 * - 配合 @Data 自动生成 getter/setter，支持运行时动态刷新
 *
 * 配置示例（application.yml）：
 *   app:
 *     reranker:
 *       enabled: true
 *       url: http://localhost:8081
 *       candidate-count: 20
 *       top-k: 5
 *       score-threshold: 0.0
 *       timeout-ms: 5000
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.reranker")
public class RerankerProperties {

    /** 是否启用 Reranker（关闭后走原有纯向量检索逻辑） */
    private boolean enabled = false;

    /** TEI Reranker 服务地址 */
    private String url = "http://localhost:8081";

    /** 向量检索阶段召回的候选文档数（送入 Reranker 的输入量） */
    private int candidateCount = 20;

    /** Rerank 后保留的最终文档数（送入 LLM 的上下文量） */
    private int topK = 5;

    /** Rerank 分数阈值，低于此值的文档丢弃 */
    private double scoreThreshold = 0.0;

    /** 单次 Rerank 请求超时（毫秒） */
    private int timeoutMs = 5000;
}
