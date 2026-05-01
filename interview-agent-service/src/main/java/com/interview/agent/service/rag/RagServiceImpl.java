package com.interview.agent.service.rag;

import com.interview.agent.common.constant.CommonConstant;
import com.interview.agent.common.exception.BusinessException;
import com.interview.agent.common.result.ResultCode;
import com.interview.agent.dao.mapper.ChatMessageMapper;
import com.interview.agent.dao.mapper.ChatSessionMapper;
import com.interview.agent.dao.mapper.KnowledgeDocumentMapper;
import com.interview.agent.model.dto.ChatRequestDTO;
import com.interview.agent.model.entity.ChatMessage;
import com.interview.agent.model.entity.ChatSession;
import com.interview.agent.model.entity.KnowledgeDocument;
import com.interview.agent.model.vo.ChatMessageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.agent.service.rag.hybrid.HybridSearchProperties;
import com.interview.agent.service.rag.hybrid.HybridSearchService;
import com.interview.agent.service.rag.reranker.RerankingDocumentPostProcessor;
import com.interview.agent.service.rag.reranker.RerankerProperties;
import com.interview.agent.service.rag.rewrite.QueryRewriteService;
import com.interview.agent.service.rag.transformer.DocumentTextCleaner;
import com.interview.agent.service.rag.transformer.SmartTextSplitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 服务实现
 *
 * 面试亮点：
 * 1. 文档导入全链路：文件解析 → 文本清洗 → 智能分块 → Embedding → PGVector 存储
 * 2. 多格式支持：PDF (ParagraphPdf/PagePdf)、Markdown (TextReader)、TXT (TextReader)
 * 3. 文本清洗优化：DocumentTextCleaner 去页眉页脚、空行、噪声、PDF 断行修复
 * 4. 智能分块优化：SmartTextSplitter 段落优先 + 重叠窗口 + 中文感知
 * 5. RAG 检索增强：问题向量化 → 相似度检索 → Reranking 精排 → Prompt 拼接 → LLM 生成
 * 6. 相似度阈值过滤：低于阈值的文档片段不参与增强，避免噪声
 * 7. Reranking 精排：向量检索 Top-20 → Cross-Encoder 精排 Top-5 → 送入 LLM
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final SmartTextSplitter smartTextSplitter;
    private final DocumentTextCleaner documentTextCleaner;
    private final RerankingDocumentPostProcessor rerankingPostProcessor;
    private final RerankerProperties rerankerProperties;
    private final HybridSearchService hybridSearchService;
    private final HybridSearchProperties hybridProperties;
    private final QueryRewriteService queryRewriteService;
    private final KnowledgeDocumentMapper documentMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("classpath:/prompts/rag-enhanced-answer.st")
    private Resource ragPromptResource;

    @Value("${spring.ai.ollama.chat.options.model}")
    private String modelName;

    /** 相似度阈值 — 低于此值的检索结果不参与增强（余弦相似度范围 -1~1，越高越相似） */
    private static final double SIMILARITY_THRESHOLD = CommonConstant.RAG_SIMILARITY_THRESHOLD;

    // ==================== RAG 对话 ====================

    @Override
    public ChatMessageVO ragChat(ChatRequestDTO request) {
        String sessionId = getOrCreateSession(request);

        // 1. 向量检索相关文档
        List<Document> similarDocs = searchSimilarDocs(request.getMessage(), request.getDomain());

        // 2. 构建RAG增强Prompt
        Prompt prompt = buildRagPrompt(request, similarDocs);

        // 3. 保存用户消息（构建Prompt后再保存，避免历史消息重复）
        saveMessage(sessionId, CommonConstant.ROLE_USER, request.getMessage());

        // 4. 调用LLM
        String response;
        try {
            log.info("[RAG-Chat] 开始同步调用LLM: model={}, sessionId={}, similarDocs={}", modelName, sessionId, similarDocs.size());
            response = chatClient.prompt(prompt)
                    .call()
                    .content();
            log.info("[RAG-Chat] LLM同步响应完成: sessionId={}, 响应长度={}", sessionId, response != null ? response.length() : 0);
            log.debug("[RAG-Chat] LLM同步响应内容:\n{}", response);
        } catch (Exception e) {
            log.error("[RAG-Chat] RAG对话调用失败: sessionId={}", sessionId, e);
            throw new BusinessException(ResultCode.RAG_SEARCH_ERROR);
        }

        // 5. 保存助手消息
        saveMessage(sessionId, CommonConstant.ROLE_ASSISTANT, response);

        ChatMessageVO vo = new ChatMessageVO();
        vo.setSessionId(sessionId);
        vo.setRole(CommonConstant.ROLE_ASSISTANT);
        vo.setContent(response);
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }

    @Override
    public Flux<String> ragChatStream(ChatRequestDTO request) {
        // sessionId 已由 Controller 通过 resolveSessionId 提前设置到 request 中
        String sessionId = request.getSessionId();

        // 1. 向量检索
        List<Document> similarDocs = searchSimilarDocs(request.getMessage(), request.getDomain());

        // 2. 构建RAG增强Prompt
        Prompt prompt = buildRagPrompt(request, similarDocs);

        // 3. 保存用户消息
        saveMessage(sessionId, CommonConstant.ROLE_USER, request.getMessage());

        // 4. 流式调用
        StringBuilder fullResponse = new StringBuilder();
        return chatClient.prompt(prompt)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    saveMessage(sessionId, CommonConstant.ROLE_ASSISTANT, fullResponse.toString());
                    log.info("[RAG-Stream] LLM流式响应完成: sessionId={}, 响应长度={}", sessionId, fullResponse.length());
                    log.debug("[RAG-Stream] LLM流式响应内容:\n{}", fullResponse.toString());
                });
    }

    // ==================== 文档导入 ====================

    @Override
    public void importDocument(String filePath, String domain, String title) {
        importDocument(filePath, domain, title, null);
    }

    /**
     * 导入文档到知识库（完整版，支持文件类型识别）
     *
     * 核心流程（面试必背）：
     * ① 文件解析 — 根据文件后缀选择对应的 DocumentReader
     * ② 文本清洗 — DocumentTextCleaner 去除页眉页脚、空行、噪声
     * ③ 文本分块 (Chunking) — SmartTextSplitter 段落优先 + 重叠窗口
     * ④ 向量化 (Embedding) — bge-m3 将文本转为 1024 维向量
     * ⑤ 存储 (VectorStore) — 向量 + 元数据写入 PGVector
     * ⑥ 记录 (RDBMS) — knowledge_document 表记录文档元信息
     */
    public void importDocument(String filePath, String domain, String title, String fileType) {
        try {
            // 识别文件类型
            String actualFileType = fileType != null ? fileType : detectFileType(filePath);
            String normalizedDomain = domain != null ? domain.toLowerCase() : "java";
            log.info("开始导入文档: filePath={}, domain={}, title={}, fileType={}", filePath, normalizedDomain, title, actualFileType);

            // ① 根据文件类型选择 Reader 解析文档
            List<Document> documents = parseDocument(filePath, actualFileType);
            log.info("文档解析完成: 共 {} 个原始文档段", documents.size());

            if (documents.isEmpty()) {
                throw new BusinessException(ResultCode.DOCUMENT_PARSE_ERROR);
            }

            // ② 文本清洗 — 去除页眉页脚、空行、噪声、PDF 断行修复
            List<Document> cleanedDocs = documentTextCleaner.apply(documents);
            log.info("文本清洗完成: 保留 {} 个有效文档段（清洗前 {}）", cleanedDocs.size(), documents.size());

            if (cleanedDocs.isEmpty()) {
                throw new BusinessException(ResultCode.DOCUMENT_PARSE_ERROR);
            }

            // ③ 文本分块 (Chunking) — 段落优先 + 重叠窗口
            List<Document> chunks = smartTextSplitter.apply(cleanedDocs);
            log.info("文本分块完成: 共 {} 个分块", chunks.size());

            // ④ 过滤过短的分块（bge-m3 对过短文本会返回 NaN 向量，导致 Ollama 报 500）
            int minChunkLength = 20;
            List<Document> validChunks = new ArrayList<>();
            for (Document chunk : chunks) {
                String text = chunk.getText();
                if (text != null && text.trim().length() >= minChunkLength) {
                    validChunks.add(chunk);
                } else {
                    log.debug("过滤短文本块: length={}, content='{}'",
                            text != null ? text.length() : 0,
                            text != null ? text.substring(0, Math.min(50, text.length())) : "null");
                }
            }
            log.info("过滤短文本块完成: 保留 {}/{} 个有效分块", validChunks.size(), chunks.size());

            if (validChunks.isEmpty()) {
                throw new BusinessException(ResultCode.DOCUMENT_PARSE_ERROR);
            }

            // ⑤ 先保存文档记录到关系数据库，获取自增 ID（用于关联向量数据）
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setTitle(title);
            doc.setDomain(normalizedDomain);
            doc.setFileType(actualFileType);
            doc.setFilePath(filePath);
            doc.setChunkCount(0);
            doc.setStatus("ACTIVE");
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.insert(doc);
            Long docId = doc.getId();
            log.info("文档记录已创建: docId={}, title={}", docId, title);

            // ⑥ 为每个块添加元数据（包含 doc_id，用于删除时关联清理）
            for (int i = 0; i < validChunks.size(); i++) {
                Document chunk = validChunks.get(i);
                chunk.getMetadata().put("doc_id", docId);
                chunk.getMetadata().put("domain", normalizedDomain);
                chunk.getMetadata().put("title", title);
                chunk.getMetadata().put("file_type", actualFileType);
                chunk.getMetadata().put("chunk_index", i);
                chunk.getMetadata().put("total_chunks", validChunks.size());
            }

            // ⑦ 逐条存入向量数据库
            int successCount = 0;
            int failCount = 0;
            for (int i = 0; i < validChunks.size(); i++) {
                Document chunk = validChunks.get(i);
                try {
                    vectorStore.add(List.of(chunk));
                    successCount++;
                    if ((i + 1) % 10 == 0 || (i + 1) == validChunks.size()) {
                        log.info("向量存储进度: {}/{}", i + 1, validChunks.size());
                    }
                } catch (Exception e) {
                    failCount++;
                    String preview = "";
                    String text = chunk.getText();
                    if (text != null) {
                        preview = text.substring(0, Math.min(50, text.length()));
                    }
                    log.warn("分块 {}/{} 向量存储失败: {} | content='{}'",
                            i + 1, validChunks.size(), e.getMessage(),
                            preview);
                }
            }

            // ⑧ 判断实际入库结果
            if (successCount == 0) {
                log.error("向量存储全部失败: 共 {} 个分块，0 个成功", validChunks.size());
                throw new BusinessException(ResultCode.VECTOR_STORE_ERROR);
            }
            if (failCount > 0) {
                log.warn("向量存储部分失败: 成功 {}/{}, 失败 {}", successCount, validChunks.size(), failCount);
            }
            log.info("向量存储完成: 成功 {}/{} 个分块已写入 PGVector", successCount, validChunks.size());

            // ⑨ 更新文档记录的实际成功分块数
            doc.setChunkCount(successCount);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);

            log.info("文档导入成功: docId={}, title={}, domain={}, fileType={}, 成功chunks={}, 失败chunks={}",
                    docId, title, normalizedDomain, actualFileType, successCount, failCount);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档导入失败: filePath={}", filePath, e);
            throw new BusinessException(ResultCode.DOCUMENT_PARSE_ERROR);
        }
    }

    @Override
    public void deleteDocument(Long documentId) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        // ① 清理 PGVector 中的向量数据
        deleteVectorsByDocId(documentId);

        // ② 逻辑删除文档（@TableLogic 会自动将 status 设为 DELETED）
        documentMapper.deleteById(documentId);
        log.info("文档已标记删除: id={}, title={}", documentId, doc.getTitle());
    }

    /**
     * 根据 doc_id 元数据清理 PGVector 中的向量数据
     *
     * 面试亮点：RAG 系统中"删文档"必须同步清理向量数据，否则：
     * 1. 向量空间膨胀，检索性能下降
     * 2. 已删除文档的内容仍可能被检索到，产生"幽灵回答"
     * 3. 存储资源浪费
     *
     * 实现方式：通过 JdbcTemplate 查询 vector_store 表中 metadata->>'doc_id' 匹配的记录 ID，
     * 再调用 VectorStore.delete() 批量删除
     */
    private void deleteVectorsByDocId(Long docId) {
        try {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'doc_id' = ?",
                    String.valueOf(docId)
            );

            if (deleted == 0) {
                log.warn("未找到 doc_id={} 对应的向量数据，可能为旧数据（无 doc_id 元数据）", docId);
                return;
            }

            log.info("PGVector 向量数据已清理: docId={}, 删除向量数={}", docId, deleted);
        } catch (Exception e) {
            log.error("清理 PGVector 向量数据失败: docId={}, 将继续标记文档删除", docId, e);
        }
    }

    @Override
    public String resolveSessionId(ChatRequestDTO request) {
        return getOrCreateSession(request);
    }

    // ==================== 私有方法 ====================

    /**
     * 根据文件类型选择对应的 DocumentReader 解析文档
     *
     * 面试亮点：不同文件格式需要不同的解析策略
     * - PDF: PagePdfDocumentReader（基于 Apache PdfBox，按页提取）
     * - MD/TXT: TextReader（纯文本直接读取）
     */
    private List<Document> parseDocument(String filePath, String fileType) {
        Resource resource = new org.springframework.core.io.FileSystemResource(filePath);

        return switch (fileType.toLowerCase()) {
            case "pdf" -> {
                List<Document> result = tryParagraphPdfReader(resource);
                if (result != null && !result.isEmpty()) {
                    log.info("[PDF] 使用 ParagraphPdfDocumentReader 解析成功: {} 段", result.size());
                    yield result;
                }
                log.info("[PDF] ParagraphPdfDocumentReader 无结果，降级为 PagePdfDocumentReader");
                PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                        .withPagesPerDocument(1)
                        .build();
                PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);
                yield pdfReader.get();
            }
            case "md", "txt" -> {
                TextReader textReader = new TextReader(resource);
                yield textReader.get();
            }
            default -> {
                log.warn("不支持的文件类型: {}, 尝试作为纯文本读取", fileType);
                TextReader fallbackReader = new TextReader(resource);
                yield fallbackReader.get();
            }
        };
    }

    /**
     * 尝试使用 ParagraphPdfDocumentReader 解析 PDF
     *
     * ParagraphPdfDocumentReader 利用 PDF 目录（TOC）信息按段落提取，
     * 比 PagePdfDocumentReader（按页提取）产生更干净的文本。
     * 但并非所有 PDF 都包含目录信息，所以需要降级处理。
     */
    private List<Document> tryParagraphPdfReader(Resource resource) {
        try {
            ParagraphPdfDocumentReader paragraphReader = new ParagraphPdfDocumentReader(resource);
            List<Document> docs = paragraphReader.get();
            if (docs != null && !docs.isEmpty()) {
                return docs;
            }
        } catch (Exception e) {
            log.debug("[PDF] ParagraphPdfDocumentReader 解析失败（PDF可能无目录信息）: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 根据文件路径识别文件类型
     */
    private String detectFileType(String filePath) {
        if (filePath == null) return "txt";
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".pdf")) return "pdf";
        if (lowerPath.endsWith(".md") || lowerPath.endsWith(".markdown")) return "md";
        return "txt";
    }

    /**
     * 向量相似度检索 + Reranking 精排（两阶段检索架构）
     *
     * 面试亮点：两阶段检索是 RAG 生产系统的标配
     * - 第一阶段（召回）：向量检索 Top-20（快但粗，Embedding 近似匹配）
     * - 第二阶段（精排）：Cross-Encoder 对 Top-20 逐一打分 → 取 Top-5（慢但准）
     *
     * 为什么不直接用向量检索 Top-5？
     * - Embedding 是双塔模型，query 和 document 编码时没有交互
     * - 语义相近但实际不相关的文档可能排在前面（如"线程安全"vs"线程池"）
     * - Cross-Encoder 在 Transformer 每一层都有 query-doc 注意力交互，精度远高于余弦相似度
     *
     * 降级策略：Reranker 不可用时自动降级为纯向量检索结果
     *
     * 混合检索扩展（Phase 2）：
     * - 向量检索擅长语义匹配（"线程池" ↔ "Thread Pool"）
     * - 关键词检索擅长精确匹配（"ThreadPoolExecutor" 完全一致）
     * - RRF（Reciprocal Rank Fusion）将两路结果按排名融合，兼顾语义和精确
     *
     * Phase 3 查询改写（Query Rewriting）：
     * - 用户原始问题 → LLM 改写为更适合检索的形式 → 用改写后的 query 检索
     * - 触发条件：问题过短(<10字) / 包含口语化词汇 / 包含指代词
     * - 业界方案参考：Dify rewrite-angular / RAGFlow 意图识别 / LlamaIndex HyDE
     */
    private List<Document> searchSimilarDocs(String query, String domain) {
        String rewrittenQuery = queryRewriteService.rewriteIfNeeded(query);
        if (!rewrittenQuery.equals(query)) {
            log.info("[RAG-Search] Query Rewriting 已改写: '{}' → '{}'", query, rewrittenQuery);
        }
        String searchQuery = rewrittenQuery;

        int candidateCount = rerankerProperties.isEnabled()
                ? rerankerProperties.getCandidateCount()
                : CommonConstant.RAG_DEFAULT_TOP_K;

        List<Document> results;
        boolean hybridSearchPerformed = false;

        if (hybridProperties.isEnabled()) {
            log.info("[RAG-Search] 混合检索模式: query='{}', domain='{}', vectorWeight={}, keywordWeight={}, topK={}",
                    searchQuery, domain, hybridProperties.getVectorWeight(), hybridProperties.getKeywordWeight(), hybridProperties.getTopK());
            results = hybridSearchService.hybridSearch(searchQuery, domain, candidateCount);
            hybridSearchPerformed = true;
        } else {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(searchQuery)
                    .topK(candidateCount)
                    .similarityThreshold(SIMILARITY_THRESHOLD);

            if (domain != null && !domain.isEmpty()) {
                builder.filterExpression("domain == '" + domain.toLowerCase() + "'");
            }

            SearchRequest searchRequest = builder.build();
            log.info("[RAG-Search] 向量检索: query='{}', domain='{}', topK={}, threshold={}, rerankerEnabled={}",
                    searchQuery, domain, candidateCount, SIMILARITY_THRESHOLD, rerankerProperties.isEnabled());

            results = vectorStore.similaritySearch(searchRequest);
        }

        log.info("[RAG-Search] 检索完成: hybrid={}, 命中 {} 条文档", hybridSearchPerformed, results.size());

        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            String docDomain = (String) doc.getMetadata().getOrDefault("domain", "");
            String docTitle = (String) doc.getMetadata().getOrDefault("title", "");
            Object distance = doc.getMetadata().get("distance");
            Object hybridScore = doc.getMetadata().get("hybrid_score");
            String text = doc.getText();
            String preview = text != null ? text.substring(0, Math.min(100, text.length())) : "null";
            log.info("[RAG-Search] 结果[{}]: domain={}, title={}, distance={}, hybrid_score={}, length={}, preview='{}'",
                    i, docDomain, docTitle, distance, hybridScore, text != null ? text.length() : 0, preview);
            // log.debug("[RAG-Search] 结果[{}] 完整内容:\n{}", i, text);
        }

        // Reranking 精排阶段
        if (rerankerProperties.isEnabled() && !results.isEmpty()) {
            List<Document> rerankedDocs = rerankingPostProcessor.rerank(query, results);
            boolean hasReranked = rerankedDocs.stream()
                    .anyMatch(doc -> doc.getMetadata().containsKey("rerank_score"));
            if (hasReranked) {
                log.info("[RAG-Search] Reranking 精排完成: 原始={}条 → 精排={}条", results.size(), rerankedDocs.size());
            } else {
                log.warn("[RAG-Search] Reranking 失败，降级使用原始检索结果: {}条", results.size());
            }
            return rerankedDocs;
        }

        return results;
    }

    /**
     * 构建RAG增强Prompt
     *
     * 面试亮点：RAG 的核心就是"检索结果注入 Prompt"
     * - System Prompt：角色设定 + 参考资料
     * - User Message：用户原始问题
     * - 检索到的文档片段作为 context 注入
     */
    private Prompt buildRagPrompt(ChatRequestDTO request, List<Document> similarDocs) {
        String context;
        if (similarDocs.isEmpty()) {
            context = "[无相关参考资料]";
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < similarDocs.size(); i++) {
                Document doc = similarDocs.get(i);
                String docDomain = (String) doc.getMetadata().getOrDefault("domain", "");
                String docTitle = (String) doc.getMetadata().getOrDefault("title", "");
                String chunkIndex = String.valueOf(doc.getMetadata().getOrDefault("chunk_index", i + 1));
                sb.append("【文档").append(i + 1).append("】领域: ").append(docDomain)
                        .append(" | 标题: ").append(docTitle)
                        .append("\n");
                sb.append(doc.getText());
                if (i < similarDocs.size() - 1) {
                    sb.append("\n\n");
                }
            }
            context = sb.toString();
        }

        String templateContent;
        try {
            templateContent = ragPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[RAG-Prompt] 读取RAG模板文件失败", e);
            throw new BusinessException(ResultCode.CHAT_ERROR);
        }
        PromptTemplate template = new PromptTemplate(templateContent);
        String systemContent = template.render(Map.of(
                "context", context,
                "question", request.getMessage()
        ));

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemContent));

        int historyCount = 0;
        if (request.getSessionId() != null) {
            List<ChatMessage> history = messageMapper.selectList(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, request.getSessionId())
                            .orderByAsc(ChatMessage::getCreatedAt)
                            .last("LIMIT 20")
            );
            historyCount = history.size();
            for (ChatMessage msg : history) {
                if (CommonConstant.ROLE_USER.equals(msg.getRole())) {
                    messages.add(new UserMessage(msg.getContent()));
                } else if (CommonConstant.ROLE_ASSISTANT.equals(msg.getRole())) {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }

        messages.add(new UserMessage(request.getMessage()));

        log.info("[RAG-Prompt] 构建完成: model={}, similarDocs={}, contextLength={}, historyCount={}, totalMessages={}, userMessage='{}'",
                modelName, similarDocs.size(), context.length(), historyCount, messages.size(),
                request.getMessage().length() > 50 ? request.getMessage().substring(0, 50) + "..." : request.getMessage());
        log.debug("[RAG-Prompt] RAG上下文内容:\n{}", context);
        log.debug("[RAG-Prompt] System Prompt内容:\n{}", systemContent);

        OllamaChatOptions.Builder optionsBuilder = OllamaChatOptions.builder();
        if (Boolean.TRUE.equals(request.getThinkingEnabled())) {
            optionsBuilder.enableThinking();
        } else {
            optionsBuilder.disableThinking();
        }

        return new Prompt(messages, optionsBuilder.build());
    }

    private String getOrCreateSession(ChatRequestDTO request) {
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            return request.getSessionId();
        }
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setDomain(request.getDomain());
        session.setModelName(modelName);
        session.setCreatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return sessionId;
    }

    private void saveMessage(String sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }
}
