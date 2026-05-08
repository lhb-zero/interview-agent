package com.interview.agent.service.chat.deepseek;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * DeepSeek ChatModel 包装器
 *
 * 包装 Spring AI 内置的 OpenAiChatModel，处理 DeepSeek 特有的扩展：
 * 1. 从 DeepSeekChatOptions 中提取 thinking 标志
 * 2. 通过 ThreadLocal 通知 DeepSeekThinkingInterceptor 注入 thinking 参数
 * 3. 将 OpenAiChatOptions 提取出来传给底层 OpenAiChatModel
 *
 * 对外透明：实现 ChatModel 接口，调用方无需感知 provider 差异。
 */
@Slf4j
public class DeepSeekChatModel implements ChatModel {

    private final ChatModel delegate;

    public DeepSeekChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        boolean thinking = false;
        Prompt actualPrompt = prompt;

        if (prompt.getOptions() instanceof DeepSeekChatOptions dsOptions) {
            thinking = dsOptions.isThinkingEnabled();
            actualPrompt = new Prompt(prompt.getInstructions(), dsOptions.getOpenAiOptions());
        }

        try {
            DeepSeekThinkingInterceptor.setThinkingEnabled(thinking);
            if (thinking) {
                log.info("[DeepSeek] 深度思考模式已启用");
            }
            return delegate.call(actualPrompt);
        } finally {
            DeepSeekThinkingInterceptor.clear();
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        boolean thinking = false;
        Prompt actualPrompt = prompt;

        if (prompt.getOptions() instanceof DeepSeekChatOptions dsOptions) {
            thinking = dsOptions.isThinkingEnabled();
            actualPrompt = new Prompt(prompt.getInstructions(), dsOptions.getOpenAiOptions());
        }

        try {
            DeepSeekThinkingInterceptor.setThinkingEnabled(thinking);
            if (thinking) {
                log.info("[DeepSeek] 深度思考模式已启用（流式）");
            }
            return delegate.stream(actualPrompt)
                    .doFinally(signal -> DeepSeekThinkingInterceptor.clear());
        } catch (Exception e) {
            DeepSeekThinkingInterceptor.clear();
            throw e;
        }
    }
}
