package com.interview.agent.service.rag.hybrid;

import com.interview.agent.common.constant.CommonConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final HybridSearchProperties hybridProperties;

    public List<Document> hybridSearch(String query, String domain, int topK) {
        if (!hybridProperties.isEnabled()) {
            return vectorSearch(query, domain, topK);
        }

        log.info("[Hybrid-Search] 混合检索开始: query='{}', domain='{}', topK={}", query, domain, topK);

        List<Document> vectorResults = vectorSearch(query, domain, hybridProperties.getTopK());
        log.info("[Hybrid-Search] 向量检索完成: 命中 {} 条", vectorResults.size());

        List<Document> keywordResults = keywordSearch(query, domain, hybridProperties.getTopK());
        log.info("[Hybrid-Search] 关键词检索完成: 命中 {} 条", keywordResults.size());

        if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
            return Collections.emptyList();
        }

        if (vectorResults.isEmpty()) {
            return keywordResults.stream().limit(topK).collect(Collectors.toList());
        }
        if (keywordResults.isEmpty()) {
            return vectorResults.stream().limit(topK).collect(Collectors.toList());
        }

        List<Document> fusedResults = rrfFusion(query, vectorResults, keywordResults, topK);
        log.info("[Hybrid-Search] RRF融合完成: 最终返回 {} 条", fusedResults.size());

        return fusedResults;
    }

    private List<Document> vectorSearch(String query, String domain, int topK) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(CommonConstant.RAG_SIMILARITY_THRESHOLD);

            if (domain != null && !domain.isEmpty()) {
                builder.filterExpression("domain == '" + domain.toLowerCase() + "'");
            }

            return vectorStore.similaritySearch(builder.build());
        } catch (Exception e) {
            log.error("[Hybrid-Search] 向量检索失败: query='{}'", query, e);
            return Collections.emptyList();
        }
    }

    private List<Document> keywordSearch(String query, String domain, int topK) {
        if (!hybridProperties.isKeywordSearchEnabled()) {
            return Collections.emptyList();
        }

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT id, content, metadata, embedding FROM vector_store WHERE 1=1 ");

            List<Object> params = new ArrayList<>();

            if (domain != null && !domain.isEmpty()) {
                sql.append("AND metadata->>'domain' = ? ");
                params.add(domain.toLowerCase());
            }

            String[] keywords = query.split("\\s+");
            if (keywords.length > 0) {
                String keywordCondition;
                if ("all".equals(hybridProperties.getKeywordMatchMode())) {
                    StringBuilder allCondition = new StringBuilder();
                    for (int i = 0; i < keywords.length; i++) {
                        if (i > 0) allCondition.append(" AND ");
                        allCondition.append("(content ILIKE ? OR content ILIKE ?)");
                        params.add("%" + keywords[i] + "%");
                        params.add("%" + keywords[i] + "%");
                    }
                    sql.append("AND (").append(allCondition).append(") ");
                } else {
                    StringBuilder anyCondition = new StringBuilder();
                    for (int i = 0; i < keywords.length; i++) {
                        if (i > 0) anyCondition.append(" OR ");
                        anyCondition.append("content ILIKE ?");
                        params.add("%" + keywords[i] + "%");
                    }
                    sql.append("AND (").append(anyCondition).append(") ");
                }
            }

            sql.append("ORDER BY id DESC LIMIT ?");
            params.add(topK * 2);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

            List<Document> documents = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String id = (String) row.get("id");
                String content = (String) row.get("content");
                Map<String, Object> metadata = (Map<String, Object>) row.get("metadata");

                if (content != null && !content.isEmpty()) {
                    Document doc = new Document(id, content, metadata);
                    doc.getMetadata().put("keyword_match", true);
                    doc.getMetadata().put("keyword_score", 1.0);
                    documents.add(doc);
                }
            }

            return documents;
        } catch (Exception e) {
            log.error("[Hybrid-Search] 关键词检索失败: query='{}'", query, e);
            return Collections.emptyList();
        }
    }

    private List<Document> rrfFusion(String query, List<Document> vectorResults,
                                      List<Document> keywordResults, int topK) {
        double rrfK = hybridProperties.getRrfK();
        int vectorWeight = hybridProperties.getVectorWeight();
        int keywordWeight = hybridProperties.getKeywordWeight();

        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, Document> docMap = new LinkedHashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            Document doc = vectorResults.get(i);
            String docId = doc.getId();
            docMap.put(docId, doc);
            double vectorScore = 1.0 - (i * 0.01);
            double weightedScore = vectorScore * vectorWeight / 100.0;
            rrfScores.merge(docId, weightedScore, (a, b) -> a + b);
            doc.getMetadata().put("vector_rank", i + 1);
        }

        Map<String, Integer> keywordRanks = new HashMap<>();
        for (int i = 0; i < keywordResults.size(); i++) {
            Document doc = keywordResults.get(i);
            String docId = doc.getId();
            keywordRanks.put(docId, i + 1);
            if (!docMap.containsKey(docId)) {
                docMap.put(docId, doc);
            }
        }

        for (Map.Entry<String, Document> entry : docMap.entrySet()) {
            String docId = entry.getKey();
            Document doc = entry.getValue();
            Integer keywordRank = keywordRanks.get(docId);

            if (keywordRank != null) {
                double keywordScore = 1.0 / (rrfK + keywordRank);
                double weightedKeywordScore = keywordScore * keywordWeight / 100.0;
                rrfScores.merge(docId, weightedKeywordScore, (a, b) -> a + b);
                doc.getMetadata().put("keyword_rank", keywordRank);
            }

            doc.getMetadata().put("hybrid_score", rrfScores.get(docId));
        }

        List<Document> sortedDocs = new ArrayList<>(docMap.values());
        sortedDocs.sort((a, b) -> {
            Double scoreA = rrfScores.get(a.getId());
            Double scoreB = rrfScores.get(b.getId());
            return Double.compare(scoreB != null ? scoreB : 0, scoreA != null ? scoreA : 0);
        });

        log.debug("[Hybrid-Search] RRF融合详情:");
        for (int i = 0; i < Math.min(10, sortedDocs.size()); i++) {
            Document doc = sortedDocs.get(i);
            Double hybridScore = rrfScores.get(doc.getId());
            Integer vectorRank = (Integer) doc.getMetadata().get("vector_rank");
            Integer keywordRank = (Integer) doc.getMetadata().get("keyword_rank");
            log.debug("[Hybrid-Search] 排名{}: id={}, hybrid_score={}, vector_rank={}, keyword_rank={}, content_preview='{}'",
                    i + 1, doc.getId(), hybridScore, vectorRank, keywordRank,
                    doc.getText().substring(0, Math.min(50, doc.getText().length())));
        }

        return sortedDocs.stream().limit(topK).collect(Collectors.toList());
    }

    public void saveTextHash(String docId, String content) {
        try {
            String hash = Integer.toHexString(content.hashCode());
            String sql = "UPDATE vector_store SET text_hash = ? WHERE id = ?";
            jdbcTemplate.update(sql, hash, docId);
        } catch (Exception e) {
            log.warn("[Hybrid-Search] 保存text_hash失败: docId={}", docId, e);
        }
    }
}