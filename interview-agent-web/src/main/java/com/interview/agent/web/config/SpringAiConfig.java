package com.interview.agent.web.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置类
 *
 * 核心配置：
 * 1. ChatClient — 对话客户端
 * 2. TokenTextSplitter — 文本分块器，用于 RAG
 *
 * 注意：Tool Calling 工具在 ChatServiceImpl 中通过 .tools() 动态注册，
 * 避免与 QuestionGenerateTool 的循环依赖问题
 */
@Configuration
public class SpringAiConfig {

    /**
     * 配置 ChatClient（不在此注册 Tool，避免循环依赖）
     *
     * Tool Calling 在 Service 层按需动态注册：
     * chatClient.prompt().tools(knowledgeSearchTool, questionGenerateTool).call()
     */
    @Bean
    public ChatClient chatClient(OllamaChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 文本分块器（用于 RAG 文档导入）
     *
     * 面试亮点：Chunking 策略影响 RAG 效果
     * - chunkSize: 每段最大 Token 数（800）— 适当增大以保留完整语义
     * - minChunkSizeChars: 分块最小字符数（50）— 过短的块丢弃
     * - minChunkLengthToEmbed: 可嵌入的最小分块长度（50）
     * - maxNumChunks: 最大分块数（10000）— 大文档兜底
     * - keepSeparator: 保留分隔符 — 维持段落结构
     *
     * 构造函数签名（Spring AI 1.1.0）：
     * TokenTextSplitter(int chunkSize, int minChunkSizeChars, int minChunkLengthToEmbed,
     *                   int maxNumChunks, boolean keepSeparator)
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(
                800,      // chunkSize — 每段最大 Token 数
                50,       // minChunkSizeChars — 分块最小字符数
                50,       // minChunkLengthToEmbed — 可嵌入的最小长度
                10000,    // maxNumChunks — 最大分块数
                true      // keepSeparator — 保留分隔符
        );
    }
}
