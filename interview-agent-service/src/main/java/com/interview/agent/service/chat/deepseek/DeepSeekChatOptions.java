package com.interview.agent.service.chat.deepseek;

import lombok.Getter;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

/**
 * DeepSeek 聊天选项
 *
 * 包装 OpenAiChatOptions 并扩展 thinking 参数。
 * 实现 ChatOptions 接口，可直接传入 Prompt 构造函数。
 *
 * DeepSeekChatModel.call() 检测到此类型时：
 * 1. 提取 thinkingEnabled 标志
 * 2. 将内部 OpenAiChatOptions 提取出来传给 OpenAiChatModel
 */
@Getter
public class DeepSeekChatOptions implements ChatOptions {

    private final OpenAiChatOptions openAiOptions;
    private final boolean thinkingEnabled;

    private DeepSeekChatOptions(OpenAiChatOptions openAiOptions, boolean thinkingEnabled) {
        this.openAiOptions = openAiOptions;
        this.thinkingEnabled = thinkingEnabled;
    }

    @Override
    public String getModel() { return openAiOptions.getModel(); }

    @Override
    public Double getFrequencyPenalty() { return openAiOptions.getFrequencyPenalty(); }

    @Override
    public Integer getMaxTokens() { return openAiOptions.getMaxTokens(); }

    @Override
    public Double getPresencePenalty() { return openAiOptions.getPresencePenalty(); }

    @Override
    public List<String> getStopSequences() { return openAiOptions.getStopSequences(); }

    @Override
    public Double getTemperature() { return openAiOptions.getTemperature(); }

    @Override
    public Double getTopP() { return openAiOptions.getTopP(); }

    @Override
    public Integer getTopK() { return openAiOptions.getTopK(); }

    @Override
    public ChatOptions copy() {
        return new DeepSeekChatOptions((OpenAiChatOptions) openAiOptions.copy(), thinkingEnabled);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final OpenAiChatOptions.Builder openAiBuilder = OpenAiChatOptions.builder();
        private boolean thinkingEnabled = false;

        public Builder model(String model) { openAiBuilder.model(model); return this; }
        public Builder temperature(double temp) { openAiBuilder.temperature(temp); return this; }
        public Builder maxTokens(int maxTokens) { openAiBuilder.maxTokens(maxTokens); return this; }
        public Builder topP(double topP) { openAiBuilder.topP(topP); return this; }
        public Builder enableThinking() { this.thinkingEnabled = true; return this; }
        public Builder disableThinking() { this.thinkingEnabled = false; return this; }

        public DeepSeekChatOptions build() {
            return new DeepSeekChatOptions(openAiBuilder.build(), thinkingEnabled);
        }
    }
}
