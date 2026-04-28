package com.interview.agent.service.rag.reranker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reranking 文档后处理器 — RAG 精排核心组件
 *
 * 面试知识点（高频考点）：
 *
 * 1. 为什么需要 Reranking？
 *    向量检索（Embedding + 余弦相似度）是"双塔模型"，query 和 document 分别编码，
 *    两者在编码阶段没有交互，只能通过向量距离近似衡量相关性。
 *    这导致语义相近但实际不相关的文档也可能被召回（如"线程安全"vs"线程池"）。
 *    Reranking 用 Cross-Encoder 对 (query, document) 逐一精排，弥补这一缺陷。
 *
 * 2. 两阶段检索架构（业界标准）：
 *    ┌─────────────────────────────────────────────────────────────┐
 *    │  第一阶段：召回 (Recall)                                     │
 *    │  向量检索 Top-K（K=20~50）                                   │
 *    │  优点：快（毫秒级）；缺点：粗（有噪声）                        │
 *    ├─────────────────────────────────────────────────────────────┤
 *    │  第二阶段：精排 (Reranking)                                  │
 *    │  Cross-Encoder 对 Top-K 逐一打分 → 取 Top-N（N=5~10）       │
 *    │  优点：准（注意力交互）；缺点：慢（百毫秒级）                  │
 *    └─────────────────────────────────────────────────────────────┘
 *
 * 3. Cross-Encoder vs Bi-Encoder（面试必问）：
 *    - Bi-Encoder（Embedding 检索）：query→向量, doc→向量, cos(q,d)
 *      交互时机：仅在最后计算余弦相似度时交互 → 快但粗
 *    - Cross-Encoder（Rerank）：[CLS] query [SEP] doc → BERT → score
 *      交互时机：Transformer 每一层都有 query-doc 注意力交互 → 慢但准
 *
 * 4. 本实现调用 HuggingFace TEI 的 /rerank 端点：
 *    - TEI 是 HuggingFace 官方推理服务器，Rust 实现，生产级稳定
 *    - bge-reranker-v2-m3：568M 参数，中文优化，XLM-RoBERTa 架构
 *    - CPU 模式 20 个文档精排约 100-300ms
 *
 * 5. 降级策略（面试亮点）：
 *    - Reranker 服务不可用时，自动降级为原始向量检索结果
 *    - 不影响核心对话功能，保证系统可用性
 *    - 这是"优雅降级"的典型实践
 */
@Slf4j
@Component
public class RerankingDocumentPostProcessor {

    private final RerankerProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RerankingDocumentPostProcessor(RerankerProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(properties.getTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 对检索结果进行 Reranking 精排
     *
     * @param query 用户原始问题
     * @param documents 向量检索返回的候选文档列表
     * @return 精排后的文档列表（按相关性降序，取 Top-K）
     */
    public List<Document> rerank(String query, List<Document> documents) {
        if (!properties.isEnabled()) {
            log.debug("[Rerank] Reranker 未启用，跳过精排");
            return documents;
        }

        if (documents == null || documents.isEmpty()) {
            log.debug("[Rerank] 候选文档为空，跳过精排");
            return documents;
        }

        if (documents.size() <= properties.getTopK()) {
            log.debug("[Rerank] 候选文档数({}) <= topK({})，无需精排", documents.size(), properties.getTopK());
            return documents;
        }

        long startTime = System.currentTimeMillis();

        try {
            List<String> texts = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.toList());

            TeiRerankerApi.Request request = new TeiRerankerApi.Request(query, texts);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(request), headers);

            String rerankUrl = properties.getUrl() + "/rerank";
            log.info("[Rerank] 调用 TEI Reranker: url={}, query='{}', candidateCount={}",
                    rerankUrl, query.length() > 50 ? query.substring(0, 50) + "..." : query, texts.size());

            String responseBody = restTemplate.postForObject(
                    rerankUrl, entity, String.class);

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("[Rerank] TEI 返回空结果，降级使用原始检索结果");
                return documents;
            }

            List<TeiRerankerApi.RerankResult> results;
            String trimmed = responseBody.trim();
            if (trimmed.startsWith("[")) {
                results = objectMapper.readValue(trimmed,
                        new TypeReference<List<TeiRerankerApi.RerankResult>>() {});
            } else {
                TeiRerankerApi.Response response = objectMapper.readValue(trimmed,
                        TeiRerankerApi.Response.class);
                results = response != null ? response.getResults() : null;
            }

            if (results == null || results.isEmpty()) {
                log.warn("[Rerank] TEI 返回空结果，降级使用原始检索结果");
                return documents;
            }

            List<Document> rerankedDocs = processRerankResults(documents, results);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[Rerank] 精排完成: 输入={}个文档, 输出={}个文档, 耗时={}ms",
                    documents.size(), rerankedDocs.size(), elapsed);

            return rerankedDocs;

        } catch (JsonProcessingException e) {
            log.error("[Rerank] JSON 序列化失败，降级使用原始检索结果", e);
            return documents;
        } catch (Exception e) {
            log.error("[Rerank] TEI Reranker 调用失败，降级使用原始检索结果: {}", e.getMessage());
            return documents;
        }
    }

    /**
     * 处理 Rerank 返回结果：按分数降序排列，应用阈值过滤，取 Top-K
     */
    private List<Document> processRerankResults(List<Document> originalDocs, List<TeiRerankerApi.RerankResult> results) {

        results.sort(Comparator.comparingDouble(TeiRerankerApi.RerankResult::getScore).reversed());

        List<Document> rerankedDocs = new ArrayList<>();
        for (int i = 0; i < results.size() && rerankedDocs.size() < properties.getTopK(); i++) {
            TeiRerankerApi.RerankResult result = results.get(i);

            if (result.getScore() < properties.getScoreThreshold()) {
                log.debug("[Rerank] 文档[{}] 分数 {} 低于阈值 {}，丢弃",
                        result.getIndex(), result.getScore(), properties.getScoreThreshold());
                continue;
            }

            if (result.getIndex() >= 0 && result.getIndex() < originalDocs.size()) {
                Document doc = originalDocs.get(result.getIndex());
                doc.getMetadata().put("rerank_score", result.getScore());
                doc.getMetadata().put("rerank_rank", rerankedDocs.size() + 1);
                rerankedDocs.add(doc);
            }
        }

        for (int i = 0; i < rerankedDocs.size(); i++) {
            Document doc = rerankedDocs.get(i);
            double score = (double) doc.getMetadata().getOrDefault("rerank_score", 0.0);
            String preview = doc.getText() != null
                    ? doc.getText().substring(0, Math.min(80, doc.getText().length()))
                    : "null";
            log.info("[Rerank] #{}: score={}, content='{}'", i + 1, String.format("%.4f", score), preview);
        }

        return rerankedDocs;
    }

    /**
     * 检查 Reranker 服务是否可用
     *
     * 用于启动时健康检查和运行时降级判断
     */
    public boolean isAvailable() {
        if (!properties.isEnabled()) {
            return false;
        }
        try {
            String healthUrl = properties.getUrl() + "/health";
            restTemplate.getForObject(healthUrl, String.class);
            return true;
        } catch (Exception e) {
            log.warn("[Rerank] TEI Reranker 服务不可用: {}", e.getMessage());
            return false;
        }
    }
}
