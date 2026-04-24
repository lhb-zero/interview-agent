package com.interview.agent.service.rag.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 语义感知文本分块器 — RAG Chunking 优化核心组件
 *
 * <p>相比 Spring AI 默认的 TokenTextSplitter，本分块器做了以下关键优化：
 *
 * <h3>1. 段落优先分割</h3>
 * <p>优先在双换行符（段落边界）处分割，保持段落的语义完整性。
 * TokenTextSplitter 只按 Token 数切割，容易在段落中间断开。
 *
 * <h3>2. 重叠窗口 (Overlap)</h3>
 * <p>相邻分块之间保留一定比例的重叠内容，解决"边界断裂"问题：
 * <ul>
 *   <li>问题：关键信息恰好在分块边界，一半在上一个块，一半在下一个块</li>
 *   <li>解决：重叠区域确保边界附近的信息在两个块中都有完整上下文</li>
 *   <li>业界推荐：重叠比例 10%~20%</li>
 * </ul>
 *
 * <h3>3. 中文感知分割</h3>
 * <p>在中文句末标点（。！？；）处优先分割，避免在句子中间断开。
 *
 * <h3>4. 元数据保留</h3>
 * <p>每个分块继承原始文档的元数据，并添加 chunk_index 和 total_chunks 信息。
 */
@Slf4j
public class SmartTextSplitter implements DocumentTransformer {

    private final int chunkSize;
    private final int overlapSize;
    private final int minChunkSize;

    private static final String[] PARAGRAPH_SEPARATORS = {"\n\n", "\n"};
    private static final String[] SENTENCE_SEPARATORS = {"。", "！", "？", "；", ".", "!", "?", ";"};

    public SmartTextSplitter(int chunkSize, int overlapSize, int minChunkSize) {
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
        this.minChunkSize = minChunkSize;
    }

    public SmartTextSplitter(int chunkSize, int overlapSize) {
        this(chunkSize, overlapSize, 50);
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> allChunks = new ArrayList<>();
        int totalInputDocs = documents.size();

        for (Document doc : documents) {
            String text = doc.getText();
            if (text == null || text.isBlank()) {
                continue;
            }

            List<String> textChunks = splitText(text);
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());

            for (int i = 0; i < textChunks.size(); i++) {
                String chunk = textChunks.get(i).trim();
                if (chunk.length() < minChunkSize) {
                    log.debug("跳过过短分块: index={}, length={}, content='{}'",
                            i, chunk.length(), chunk.substring(0, Math.min(30, chunk.length())));
                    continue;
                }

                Map<String, Object> chunkMetadata = new HashMap<>(metadata);
                chunkMetadata.put("chunk_index", i);
                chunkMetadata.put("total_chunks", textChunks.size());

                allChunks.add(new Document(chunk, chunkMetadata));
            }
        }

        log.info("[SmartSplitter] 分块完成: 输入 {} 个文档, 输出 {} 个分块, " +
                        "chunkSize={}, overlap={}",
                totalInputDocs, allChunks.size(), chunkSize, overlapSize);

        return allChunks;
    }

    private List<String> splitText(String text) {
        if (text.length() <= chunkSize) {
            return List.of(text);
        }

        List<String> paragraphs = splitByParagraphs(text);
        List<String> chunks = mergeIntoChunks(paragraphs);

        return chunks;
    }

    private List<String> splitByParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        String[] splits = text.split("\\n\\n");
        for (String split : splits) {
            String trimmed = split.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }

        if (paragraphs.isEmpty()) {
            splits = text.split("\\n");
            for (String split : splits) {
                String trimmed = split.trim();
                if (!trimmed.isEmpty()) {
                    paragraphs.add(trimmed);
                }
            }
        }

        if (paragraphs.isEmpty()) {
            paragraphs.add(text.trim());
        }

        return paragraphs;
    }

    private List<String> mergeIntoChunks(List<String> paragraphs) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        String overlapBuffer = "";

        for (int i = 0; i < paragraphs.size(); i++) {
            String paragraph = paragraphs.get(i);

            if (currentChunk.length() > 0 && currentChunk.length() + paragraph.length() + 1 > chunkSize) {
                String chunkText = currentChunk.toString().trim();
                if (chunkText.length() >= minChunkSize) {
                    chunks.add(chunkText);
                }

                overlapBuffer = extractOverlap(chunkText);
                currentChunk = new StringBuilder();

                if (!overlapBuffer.isEmpty()) {
                    currentChunk.append(overlapBuffer);
                }
            }

            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);

            if (currentChunk.length() > chunkSize * 1.5) {
                List<String> subChunks = splitLongText(currentChunk.toString());
                for (int j = 0; j < subChunks.size() - 1; j++) {
                    String subChunk = subChunks.get(j).trim();
                    if (subChunk.length() >= minChunkSize) {
                        chunks.add(subChunk);
                    }
                }
                currentChunk = new StringBuilder(subChunks.get(subChunks.size() - 1));
            }
        }

        String lastChunk = currentChunk.toString().trim();
        if (lastChunk.length() >= minChunkSize) {
            chunks.add(lastChunk);
        } else if (!chunks.isEmpty() && !lastChunk.isEmpty()) {
            String previousChunk = chunks.remove(chunks.size() - 1);
            chunks.add((previousChunk + "\n\n" + lastChunk).trim());
        }

        return chunks;
    }

    private String extractOverlap(String chunkText) {
        if (overlapSize <= 0 || chunkText.length() <= overlapSize) {
            return "";
        }

        int start = chunkText.length() - overlapSize;
        String overlap = chunkText.substring(start);

        int sentenceBreak = -1;
        for (String sep : SENTENCE_SEPARATORS) {
            int idx = overlap.indexOf(sep);
            if (idx >= 0 && (sentenceBreak < 0 || idx < sentenceBreak)) {
                sentenceBreak = idx + sep.length();
            }
        }

        if (sentenceBreak > 0) {
            overlap = overlap.substring(sentenceBreak);
        }

        return overlap;
    }

    private List<String> splitLongText(String text) {
        List<String> result = new ArrayList<>();

        if (text.length() <= chunkSize) {
            result.add(text);
            return result;
        }

        int splitPos = findBestSplitPosition(text, chunkSize);
        String firstPart = text.substring(0, splitPos).trim();
        String remaining = text.substring(splitPos).trim();

        result.add(firstPart);

        if (remaining.length() > chunkSize) {
            result.addAll(splitLongText(remaining));
        } else {
            result.add(remaining);
        }

        return result;
    }

    private int findBestSplitPosition(String text, int targetPos) {
        if (targetPos >= text.length()) {
            return text.length();
        }

        int searchStart = Math.max(0, targetPos - chunkSize / 4);
        int searchEnd = Math.min(text.length(), targetPos + chunkSize / 4);

        int bestPos = targetPos;

        for (String sep : PARAGRAPH_SEPARATORS) {
            int idx = text.lastIndexOf(sep, searchEnd);
            if (idx >= searchStart && idx < searchEnd) {
                return idx + sep.length();
            }
        }

        for (String sep : SENTENCE_SEPARATORS) {
            int idx = text.lastIndexOf(sep, searchEnd);
            if (idx >= searchStart && idx < searchEnd) {
                return idx + sep.length();
            }
        }

        for (int i = searchEnd; i >= searchStart; i--) {
            if (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }

        return bestPos;
    }
}
