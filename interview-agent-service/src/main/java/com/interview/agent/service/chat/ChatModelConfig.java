package com.interview.agent.service.chat;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ChatModel Bean 优先级控制
 *
 * Spring AI 自动配置通过 spring.ai.model.chat 属性决定创建哪个 ChatModel：
 * - spring.ai.model.chat=ollama → OllamaChatAutoConfiguration 创建 OllamaChatModel
 * - spring.ai.model.chat=openai → OpenAiChatAutoConfiguration 创建 OpenAiChatModel
 *
 * 但当两个 starter 都在 classpath 时，可能出现两个 ChatModel Bean。
 * 此配置类通过 @Primary 确保注入无歧义。
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
}
