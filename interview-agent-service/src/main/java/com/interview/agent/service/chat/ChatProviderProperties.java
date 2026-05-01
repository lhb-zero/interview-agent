package com.interview.agent.service.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 聊天模型提供商配置
 *
 * 通过 spring.ai.model.chat 控制使用哪个 ChatModel：
 * - ollama（默认）→ OllamaChatModel（本地 Ollama）
 * - openai        → OpenAiChatModel（OpenAI 兼容 API，如 MiMo-V2.5-Pro）
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.model")
public class ChatProviderProperties {

    /** 提供商类型：ollama 或 openai（对应 spring.ai.model.chat） */
    private String chat = "ollama";

    /** 当前模型名称（用于日志和会话记录） */
    private String modelName = "qwen3:1.7b";

    public String getProvider() {
        return chat;
    }
}
