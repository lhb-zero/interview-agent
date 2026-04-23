package com.interview.agent.service.tool;

import com.interview.agent.common.constant.CommonConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 面试题生成工具 — LLM 可自主调用此工具按指定难度和领域生成面试题
 *
 * 面试亮点：LLM 理解用户上下文后，自主决定调用此工具，
 * 而非开发者硬编码生成逻辑
 *
 * 注意：此类使用 @Lazy 延迟注入 OllamaChatModel，避免循环依赖：
 * OllamaChatModel 自动发现 @Tool Bean -> QuestionGenerateTool -> OllamaChatModel
 */
@Slf4j
@Component
public class QuestionGenerateTool {

    private final OllamaChatModel chatModel;

    @Lazy
    public QuestionGenerateTool(OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Tool(description = "根据技术领域和难度生成面试题。"
            + "当用户明确要求生成面试题或需要按难度分类时，使用此工具。")
    public String generateQuestions(
            @ToolParam(description = "技术领域，例如：java/python/ai") String domain,
            @ToolParam(description = "难度级别，可选值：基础/中级/高级") String difficulty,
            @ToolParam(description = "生成面试题数量，默认3") int count
    ) {
        log.info("Tool Calling - QuestionGenerateTool: domain={}, difficulty={}, count={}", domain, difficulty, count);

        try {
            int questionCount = count > 0 ? count : 3;
            String prompt = String.format(
                    "请为%s领域生成%d道%s面试题，每道题需要包含：\n" +
                    "1. 题目\n" +
                    "2. 详细解析\n" +
                    "3. 相关知识点\n" +
                    "请用中文回答，格式清晰。",
                    domain, questionCount, difficulty
            );

            OllamaChatOptions options = OllamaChatOptions.builder()
                    .model(CommonConstant.DEFAULT_CHAT_MODEL)
                    .build();
            return chatModel.call(new Prompt(new UserMessage(prompt), options))
                    .getResult()
                    .getOutput()
                    .getText();
        } catch (Exception e) {
            log.error("面试题生成失败: domain={}, difficulty={}", domain, difficulty, e);
            return "面试题生成失败，请稍后重试。";
        }
    }
}
