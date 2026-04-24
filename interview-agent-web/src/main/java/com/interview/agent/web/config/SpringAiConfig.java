package com.interview.agent.web.config;

import com.interview.agent.service.rag.transformer.DocumentTextCleaner;
import com.interview.agent.service.rag.transformer.SmartTextSplitter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置类
 *
 * 核心配置：
 * 1. ChatClient — 对话客户端
 * 2. DocumentTextCleaner — 文档文本清洗器（RAG 数据预处理）
 * 3. SmartTextSplitter — 语义感知分块器（段落优先 + 重叠窗口）
 *
 * 注意：Tool Calling 工具在 ChatServiceImpl 中通过 .tools() 动态注册，
 * 避免与 QuestionGenerateTool 的循环依赖问题
 */
@Configuration
public class SpringAiConfig {

    @Bean
    public ChatClient chatClient(OllamaChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 文档文本清洗器 — RAG 数据预处理核心组件
     *
     * 解决的问题：PDF 提取的原始文本包含大量格式噪声
     * - 页眉页脚（日期戳、页码、重复标题行）
     * - 多余空行和空白符
     * - PDF 断行（中文句子被拆成多行）
     * - 控制字符
     *
     * 清洗策略：
     * - 规则化去噪：正则匹配页眉页脚、页码、重复标题行
     * - 空白符规范化：多空格→单空格，多空行→单空行
     * - PDF 断行修复：中文行尾无标点时合并为连续段落
     * - 短行过滤：移除过短的无意义行
     */
    @Bean
    public DocumentTextCleaner documentTextCleaner() {
        return new DocumentTextCleaner(3);
    }

    /**
     * 语义感知文本分块器 — RAG Chunking 优化核心组件
     *
     * 相比默认 TokenTextSplitter 的优化：
     * 1. 段落优先分割 — 优先在双换行符（段落边界）处分割，保持语义完整
     * 2. 重叠窗口 (Overlap) — 相邻分块保留重叠内容，解决边界断裂问题
     * 3. 中文感知 — 在中文句末标点处优先分割
     * 4. 长文本递归分割 — 超长段落递归切分到句子级别
     *
     * 参数说明：
     * - chunkSize=800: 每段最大字符数（中文约 400~500 字）
     * - overlapSize=100: 重叠区域 100 字符（约 12.5%，业界推荐 10%~20%）
     * - minChunkSize=50: 低于 50 字符的块丢弃
     */
    @Bean
    public SmartTextSplitter smartTextSplitter() {
        return new SmartTextSplitter(800, 100, 50);
    }
}
