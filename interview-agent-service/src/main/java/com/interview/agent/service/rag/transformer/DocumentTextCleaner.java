package com.interview.agent.service.rag.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文档文本清洗器 — RAG 数据预处理核心组件
 *
 * <p>解决的核心问题：PDF 提取的原始文本包含大量格式噪声，直接进入 Chunking + Embedding
 * 会导致：
 * <ul>
 *   <li>向量质量差 — 噪声文本的 Embedding 语义模糊，检索精度低</li>
 *   <li>Prompt 污染 — 检索到的噪声片段注入 LLM 上下文，降低回答质量</li>
 *   <li>Token 浪费 — 空行和无意义内容占用宝贵的上下文窗口</li>
 * </ul>
 *
 * <p>清洗策略（参考业界 RAG 最佳实践）：
 * <ol>
 *   <li>页眉页脚移除 — 日期戳、页码、重复标题行</li>
 *   <li>空白符规范化 — 多空格→单空格，多空行→单空行</li>
 *   <li>控制字符清理 — 移除不可见字符（0x00-0x1F, 0x7F-0x9F）</li>
 *   <li>PDF 断行修复 — 中文行尾无标点时合并为连续段落</li>
 *   <li>短行过滤 — 过滤过短的无意义行（如独立页码）</li>
 * </ol>
 */
@Slf4j
public class DocumentTextCleaner implements DocumentTransformer {

    private static final Pattern PAGE_HEADER_DATE = Pattern.compile(
            "^\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}\\s+\\d{1,2}:\\d{2}.*$"
    );

    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "^\\s*\\d+\\s*$"
    );

    private static final Pattern REPEATED_HEADER = Pattern.compile(
            "^.{5,100}\\|\\s*.{2,50}\\|\\s*.{2,50}$"
    );

    private static final Pattern CONTROL_CHARS = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F]"
    );

    private static final Pattern MULTIPLE_SPACES = Pattern.compile(
            "[ \\t]{2,}"
    );

    private static final Pattern MULTIPLE_BLANK_LINES = Pattern.compile(
            "\\n{3,}"
    );

    private static final Pattern TRAILING_SPACES = Pattern.compile(
            "[ \\t]+\\n"
    );

    private static final Pattern CHINESE_SENTENCE_END = Pattern.compile(
            "[。！？；…」）》】]"
    );

    private final int minLineLength;

    public DocumentTextCleaner() {
        this(3);
    }

    public DocumentTextCleaner(int minLineLength) {
        this.minLineLength = minLineLength;
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> cleaned = new ArrayList<>(documents.size());
        int totalOriginalLength = 0;
        int totalCleanedLength = 0;

        for (Document doc : documents) {
            String original = doc.getText();
            totalOriginalLength += original != null ? original.length() : 0;

            String cleanedText = cleanText(original);
            totalCleanedLength += cleanedText.length();

            if (cleanedText.isBlank()) {
                log.debug("清洗后文档为空，跳过: metadata={}", doc.getMetadata());
                continue;
            }

            Document cleanedDoc = new Document(cleanedText, doc.getMetadata());
            cleaned.add(cleanedDoc);
        }

        double reduction = totalOriginalLength > 0
                ? (1.0 - (double) totalCleanedLength / totalOriginalLength) * 100
                : 0;
        log.info("[TextCleaner] 清洗完成: 输入 {} 个文档, 输出 {} 个, " +
                        "原文总长={}, 清洗后总长={}, 压缩率={}%",
                documents.size(), cleaned.size(),
                totalOriginalLength, totalCleanedLength, String.format("%.1f", reduction));

        return cleaned;
    }

    public String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String result = text;

        result = CONTROL_CHARS.matcher(result).replaceAll("");

        result = fixPdfLineBreaks(result);

        String[] lines = result.split("\\n");
        List<String> keptLines = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                keptLines.add("");
                continue;
            }

            if (PAGE_HEADER_DATE.matcher(trimmed).matches()) {
                log.debug("移除页眉(日期): '{}'", trimmed);
                continue;
            }

            if (PAGE_NUMBER.matcher(trimmed).matches()) {
                log.debug("移除页码: '{}'", trimmed);
                continue;
            }

            if (REPEATED_HEADER.matcher(trimmed).matches()) {
                log.debug("移除重复标题行: '{}'", trimmed);
                continue;
            }

            if (trimmed.length() < minLineLength && !CHINESE_SENTENCE_END.matcher(trimmed).find()) {
                log.debug("移除短行: '{}'", trimmed);
                continue;
            }

            String normalized = MULTIPLE_SPACES.matcher(trimmed).replaceAll(" ");
            keptLines.add(normalized);
        }

        result = String.join("\n", keptLines);

        result = TRAILING_SPACES.matcher(result).replaceAll("\n");

        result = MULTIPLE_BLANK_LINES.matcher(result).replaceAll("\n\n");

        result = result.trim();

        return result;
    }

    /**
     * 修复 PDF 断行问题
     *
     * PDF 文本提取时，同一句中文经常被拆成多行，例如：
     * "多态是指子类可以替换父类，在实际的代码运行过程中，调用子类的方法实现。多态这种特性也"
     * "需要编程语言提供特殊的语法机制来实现，比如继承、接口类。"
     *
     * 修复逻辑：如果一行以中文字符结尾（非句末标点），且下一行以中文字符开头，
     * 则将两行合并为一行（中间不加空格，因为中文不需要空格分隔）
     */
    private String fixPdfLineBreaks(String text) {
        String[] lines = text.split("\\n", -1);
        if (lines.length <= 1) {
            return text;
        }

        StringBuilder sb = new StringBuilder();
        String previousLine = "";

        for (int i = 0; i < lines.length; i++) {
            String currentLine = lines[i];
            String trimmedCurrent = currentLine.trim();
            String trimmedPrevious = previousLine.trim();

            if (trimmedPrevious.isEmpty() || trimmedCurrent.isEmpty()) {
                sb.append(currentLine);
                if (i < lines.length - 1) {
                    sb.append("\n");
                }
                previousLine = currentLine;
                continue;
            }

            char lastChar = trimmedPrevious.charAt(trimmedPrevious.length() - 1);
            char firstChar = trimmedCurrent.charAt(0);

            if (shouldMergeLines(lastChar, firstChar)) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                    sb.deleteCharAt(sb.length() - 1);
                }
                sb.append(trimmedCurrent);
            } else {
                sb.append(currentLine);
                if (i < lines.length - 1) {
                    sb.append("\n");
                }
            }

            previousLine = currentLine;
        }

        return sb.toString();
    }

    private boolean shouldMergeLines(char lastChar, char firstChar) {
        boolean lastIsChinese = isChinese(lastChar);
        boolean firstIsChinese = isChinese(firstChar);
        boolean lastIsSentenceEnd = isSentenceEnd(lastChar);
        boolean nextStartsNewSentence = isSentenceStart(firstChar);

        if (lastIsChinese && firstIsChinese && !lastIsSentenceEnd && !nextStartsNewSentence) {
            return true;
        }

        if (lastChar == '，' || lastChar == '、' || lastChar == '：' || lastChar == '；') {
            return true;
        }

        if (lastChar == '-' && firstChar != '-') {
            return true;
        }

        return false;
    }

    private boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private boolean isSentenceEnd(char c) {
        return c == '。' || c == '！' || c == '？' || c == '；' || c == '…'
                || c == '.' || c == '!' || c == '?' || c == ';'
                || c == '」' || c == '》' || c == '）' || c == ')'
                || c == '】' || c == ']';
    }

    private boolean isSentenceStart(char c) {
        return c == '「' || c == '《' || c == '（' || c == '('
                || c == '【' || c == '[' || c == '—' || c == '"';
    }
}
