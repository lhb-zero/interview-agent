package com.interview.agent.service.chat;

import com.interview.agent.service.chat.deepseek.DeepSeekChatOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;

/**
 * 聊天选项工厂 — 消除 Service 层对具体 Provider 的依赖
 *
 * 根据当前激活的 provider（ollama/deepseek）构建对应的 ChatOptions。
 * Service 层通过此工厂创建选项，无需直接引用 OllamaChatOptions 或 DeepSeekChatOptions。
 */
@Component
@RequiredArgsConstructor
public class ChatOptionsFactory {

    private final ChatProviderProperties properties;

    /**
     * 构建带深度思考控制的选项
     */
    public ChatOptions buildThinkingOptions(boolean thinkingEnabled) {
        if (properties.isDeepSeek()) {
            DeepSeekChatOptions.Builder builder = DeepSeekChatOptions.builder();
            return thinkingEnabled ? builder.enableThinking().build() : builder.disableThinking().build();
        }
        OllamaChatOptions.Builder builder = OllamaChatOptions.builder();
        return thinkingEnabled ? builder.enableThinking().build() : builder.disableThinking().build();
    }

    /**
     * 构建 Judge 选项，模型名自动跟当前 Provider 走（用于 LlmJudgeClient）
     */
    public ChatOptions buildJudgeOptions(double temperature, int numCtx) {
        return buildJudgeOptions(properties.getModelName(), temperature, numCtx);
    }

    /**
     * 构建带显式模型参数的选项（用于 LlmJudgeClient）
     * 优先使用传入的 model，若为空则回退到当前 Provider 的默认模型
     */
    public ChatOptions buildJudgeOptions(String model, double temperature, int numCtx) {
        String resolvedModel = (model == null || model.isBlank()) ? properties.getModelName() : model;
        if (properties.isDeepSeek()) {
            return DeepSeekChatOptions.builder()
                    .model(resolvedModel)
                    .temperature(temperature)
                    .build();
        }
        return OllamaChatOptions.builder()
                .model(resolvedModel)
                .temperature(temperature)
                .numCtx(numCtx)
                .build();
    }

    /**
     * 构建空默认选项
     */
    public ChatOptions buildDefaultOptions() {
        if (properties.isDeepSeek()) {
            return DeepSeekChatOptions.builder().build();
        }
        return OllamaChatOptions.builder().build();
    }
}
