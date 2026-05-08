package com.interview.agent.service.chat;

import com.interview.agent.service.chat.deepseek.DeepSeekChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ChatModel Bean 优先级控制
 *
 * 通过 spring.ai.model.chat 属性决定使用哪个 ChatModel：
 * - ollama   → OllamaChatModel（本地 Ollama）
 * - openai   → OpenAiChatModel（原生 OpenAI）
 * - deepseek → DeepSeekChatModel（包装 OpenAiChatModel，注入 thinking 参数）
 */
@Configuration
public class ChatModelConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "ollama", matchIfMissing = true)
    public ChatModel ollamaPrimaryChatModel(ChatModel ollamaChatModel) {
        return ollamaChatModel;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai")
    public ChatModel openaiPrimaryChatModel(ChatModel openAiChatModel) {
        return openAiChatModel;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "deepseek")
    public ChatModel deepSeekPrimaryChatModel(DeepSeekChatModel deepSeekChatModel) {
        return deepSeekChatModel;
    }
}
