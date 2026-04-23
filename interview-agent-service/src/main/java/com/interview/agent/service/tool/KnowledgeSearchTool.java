package com.interview.agent.service.tool;

import com.interview.agent.common.constant.CommonConstant;
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
 * 面试亮点：Tool Calling 让 LLM 自主决定是否调用工具，
 * 而非硬编码 if-else，体现 AI Agent 思想
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool {

    private final VectorStore vectorStore;

    @Tool(description = "从面试知识库中检索与用户问题相关的面试题和知识点。"
            + "当用户询问特定技术领域的面试内容时，使用此工具获取相关知识。")
    public String searchKnowledge(
            @ToolParam(description = "搜索关键词或问题，例如：Java线程池、Python装饰器、AI模型训练") String query,
            @ToolParam(description = "技术领域，可选值：java/python/ai/frontend/database/system") String domain,
            @ToolParam(description = "返回结果数量，默认5") int topK
    ) {
        log.info("Tool Calling - KnowledgeSearchTool: query={}, domain={}, topK={}", query, domain, topK);

        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(topK > 0 ? topK : CommonConstant.RAG_DEFAULT_TOP_K);

            // 如果指定了领域，添加过滤条件
            if (domain != null && !domain.isEmpty()) {
                builder.filterExpression("domain == '" + domain + "'");
            }

            List<Document> results = vectorStore.similaritySearch(builder.build());

            if (results.isEmpty()) {
                return "未找到相关的面试知识，请尝试其他关键词。";
            }

            return results.stream()
                    .map(doc -> {
                        String docDomain = (String) doc.getMetadata().getOrDefault("domain", "");
                        String title = (String) doc.getMetadata().getOrDefault("title", "");
                        return "【" + docDomain + " - " + title + "】\n" + doc.getText();
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            log.error("知识库检索失败: query={}", query, e);
            return "知识库检索失败，请稍后重试。";
        }
    }
}
