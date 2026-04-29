package com.interview.agent.service.rag.rewrite;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteService {

    private final OllamaChatModel chatModel;
    private final QueryRewriteProperties properties;

    @Value("classpath:/prompts/query-rewrite.st")
    private Resource rewritePromptResource;

    public String rewriteIfNeeded(String query) {
        if (!properties.isEnabled()) {
            log.debug("[Query-Rewrite] 功能已禁用，返回原始查询: {}", query);
            return query;
        }

        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        boolean needsRewrite = analyzeIfRewriteNeeded(query);
        log.info("[Query-Rewrite] 改写判断: query='{}', needsRewrite={}", query, needsRewrite);

        if (!needsRewrite) {
            log.debug("[Query-Rewrite] 问题无需改写，直接返回: {}", query);
            return query;
        }

        return rewrite(query);
    }

    public String rewrite(String query) {
        String templateContent;
        try {
            templateContent = rewritePromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[Query-Rewrite] 读取改写模板失败", e);
            return query;
        }

        log.info("[Query-Rewrite] 开始改写: original='{}'", query);

        try {
            OllamaChatOptions options = OllamaChatOptions.builder()
                    .build();

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(templateContent),
                    new UserMessage(query)
            ), options);

            String rewritten = chatModel.call(prompt)
                    .getResult()
                    .getOutput()
                    .getText();

            if (rewritten == null || rewritten.trim().isEmpty()) {
                log.warn("[Query-Rewrite] 改写结果为空，返回原始查询");
                return query;
            }

            String cleaned = cleanedRewrittenResult(rewritten);
            log.info("[Query-Rewrite] 改写完成: original='{}' → rewritten='{}'", query, cleaned);

            return cleaned;
        } catch (Exception e) {
            log.error("[Query-Rewrite] 改写调用失败，返回原始查询: {}", query, e);
            return query;
        }
    }

    private boolean analyzeIfRewriteNeeded(String query) {
        String trimmed = query.trim();

        if (trimmed.length() < properties.getMinQueryLength() && properties.isNeedRewriteWhenShort()) {
            log.debug("[Query-Rewrite] 触发条件: 问题过短({} < {})", trimmed.length(), properties.getMinQueryLength());
            return true;
        }

        if (properties.isNeedRewriteWhenColloquial() && containsColloquial(query)) {
            log.debug("[Query-Rewrite] 触发条件: 包含口语化词汇");
            return true;
        }

        if (properties.isNeedRewriteWhenHasPronoun() && containsPronoun(query)) {
            log.debug("[Query-Rewrite] 触发条件: 包含指代词");
            return true;
        }

        return false;
    }

    private boolean containsColloquial(String query) {
        return Pattern.matches(properties.getColloquialPattern(), query);
    }

    private boolean containsPronoun(String query) {
        return Pattern.matches(properties.getPronounPattern(), query);
    }

    private String cleanedRewrittenResult(String result) {
        String cleaned = result.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        if (cleaned.startsWith("'") && cleaned.endsWith("'")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.trim();
    }
}