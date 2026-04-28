package com.interview.agent.service.tool;

import com.interview.agent.common.constant.CommonConstant;
import com.interview.agent.service.rag.reranker.RerankingDocumentPostProcessor;
import com.interview.agent.service.rag.reranker.RerankerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库检索工具 — LLM 可自主调用此工具检索面试知识
 *
 * 面试亮点：
 * 1. Tool Calling 让 LLM 自主决定是否调用工具，而非硬编码 if-else，体现 AI Agent 思想
 * 2. 集成 Reranking 精排：向量检索扩大召回 → Cross-Encoder 精排 → 返回高质量结果
 * 3. 降级策略：Reranker 不可用时自动降级为纯向量检索
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool {

    private final VectorStore vectorStore;
    private final RerankingDocumentPostProcessor rerankingPostProcessor;
    private final RerankerProperties rerankerProperties;

    @Tool(description = "从面试知识库中检索与用户问题相关的面试题和知识点。"
            + "当用户询问特定技术领域的面试内容时，使用此工具获取相关知识。")
    public String searchKnowledge(
            @ToolParam(description = "搜索关键词或问题，例如：Java线程池、Python装饰器、AI模型训练") String query,
            @ToolParam(description = "技术领域，可选值：java/python/ai/frontend/database/system") String domain,
            @ToolParam(description = "返回结果数量，默认5") int topK
    ) {
        log.info("Tool Calling - KnowledgeSearchTool: query={}, domain={}, topK={}", query, domain, topK);

        try {
            // 向量检索阶段：Reranker 启用时扩大召回量
            int candidateCount = rerankerProperties.isEnabled()
                    ? rerankerProperties.getCandidateCount()
                    : (topK > 0 ? topK : CommonConstant.RAG_DEFAULT_TOP_K);

            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(candidateCount)
                    .similarityThreshold(CommonConstant.RAG_SIMILARITY_THRESHOLD);

            if (domain != null && !domain.isEmpty()) {
                builder.filterExpression("domain == '" + domain.toLowerCase() + "'");
            }

            List<Document> results = vectorStore.similaritySearch(builder.build());

            if (results.isEmpty()) {
                return "未找到相关的面试知识，请尝试其他关键词。";
            }

            // Reranking 精排阶段
            if (rerankerProperties.isEnabled()) {
                results = rerankingPostProcessor.rerank(query, results);
                boolean hasReranked = results.stream()
                        .anyMatch(doc -> doc.getMetadata().containsKey("rerank_score"));
                if (hasReranked) {
                    log.info("Tool Calling - KnowledgeSearchTool Rerank: 候选={} → 精排={}", candidateCount, results.size());
                } else {
                    log.warn("Tool Calling - KnowledgeSearchTool Rerank 失败，降级使用原始检索结果: {}条", results.size());
                }
            }

            return results.stream()
                    .map(doc -> {
                        String docDomain = (String) doc.getMetadata().getOrDefault("domain", "");
                        String title = (String) doc.getMetadata().getOrDefault("title", "");
                        Object rerankScore = doc.getMetadata().get("rerank_score");
                        String scoreInfo = rerankScore != null ? " (相关度: " + String.format("%.2f", (Double) rerankScore) + ")" : "";
                        return "【" + docDomain + " - " + title + "】" + scoreInfo + "\n" + doc.getText();
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            log.error("知识库检索失败: query={}", query, e);
            return "知识库检索失败，请稍后重试。";
        }
    }
}
