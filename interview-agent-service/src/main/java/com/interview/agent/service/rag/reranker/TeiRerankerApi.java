package com.interview.agent.service.rag.reranker;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * TEI Reranker API 请求/响应数据结构
 *
 * 面试知识点：TEI /rerank 端点协议
 *
 * 请求格式：
 *   POST /rerank
 *   {
 *     "query": "Java线程池",
 *     "texts": ["线程池的核心参数...", "HashMap的底层实现..."],
 *     "raw_scores": false    // true 返回原始 logit 分数，false 返回归一化 0~1 分数
 *   }
 *
 * 响应格式（TEI 版本差异）：
 *   TEI CPU 版本（Candle 后端）返回 JSON 数组：
 *     [{"index": 0, "score": 0.92}, {"index": 2, "score": 0.45}]
 *
 *   TEI GPU 版本（ORT 后端）返回 JSON 对象：
 *     {"results": [{"index": 0, "score": 0.92, "text": "..."}]}
 *
 *   本实现兼容两种格式
 *
 * Cross-Encoder 原理（面试高频）：
 * - 双塔模型（Embedding 检索）：query 和 document 分别编码为向量，计算余弦相似度
 *   优点：快（向量可预计算）；缺点：query 和 document 之间没有交互，精度有限
 * - 交叉编码器（Cross-Encoder Rerank）：query 和 document 拼接后一起编码
 *   优点：query 和 document 在 Transformer 的每一层都有注意力交互，精度高
 *   缺点：慢（无法预计算，每个 query-document 对都要完整前向传播）
 * - 所以 RAG 采用两阶段：先用双塔模型快速召回 Top-K，再用 Cross-Encoder 精排
 */
public class TeiRerankerApi {

    @Data
    public static class Request {
        private String query;
        private List<String> texts;
        @JsonProperty("raw_scores")
        private boolean rawScores = false;

        public Request(String query, List<String> texts) {
            this.query = query;
            this.texts = texts;
        }
    }

    @Data
    public static class Response {
        private List<RerankResult> results;
    }

    @Data
    public static class RerankResult {
        /** 原始文本在 texts 列表中的索引 */
        private int index;
        /** 相关性分数（raw_scores=false 时为 0~1 归一化分数） */
        private double score;
        /** 原始文本内容 */
        private String text;
    }
}
