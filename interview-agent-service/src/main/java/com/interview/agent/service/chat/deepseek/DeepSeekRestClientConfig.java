package com.interview.agent.service.chat.deepseek;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * DeepSeek 自动配置
 *
 * 当 spring.ai.model.chat=deepseek 时激活：
 * 1. 创建指向 DeepSeek API 的 OpenAiApi（带 thinking 拦截器 + 超时配置）
 * 2. 创建 OpenAiChatModel
 * 3. 包装为 DeepSeekChatModel（处理 thinking 参数注入）
 *
 * 注意：Embedding 模型不受影响，仍使用 Ollama（spring.ai.model.chat 只控制 ChatModel）
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "deepseek")
public class DeepSeekRestClientConfig {

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Bean
    public OpenAiApi deepSeekOpenAiApi() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new DeepSeekThinkingInterceptor());

        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();
    }

    @Bean
    public OpenAiChatModel deepSeekOpenAiChatModel(OpenAiApi deepSeekOpenAiApi) {
        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model("deepseek-v4-pro")
                .temperature(0.5)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(deepSeekOpenAiApi)
                .defaultOptions(defaultOptions)
                .retryTemplate(RetryTemplate.defaultInstance())
                .build();
    }

    @Bean
    public DeepSeekChatModel deepSeekChatModel(OpenAiChatModel deepSeekOpenAiChatModel) {
        return new DeepSeekChatModel(deepSeekOpenAiChatModel);
    }
}
