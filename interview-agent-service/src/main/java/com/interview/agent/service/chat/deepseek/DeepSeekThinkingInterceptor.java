package com.interview.agent.service.chat.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * DeepSeek 深度思考 HTTP 拦截器
 *
 * 通过 ThreadLocal 控制，在请求体中注入 {"thinking": {"type": "enabled"}} 参数。
 * 仅当 thinking 开启且请求目标为 /chat/completions 时才修改请求体。
 *
 * 设计要点：
 * - ThreadLocal 保证每个请求独立，避免并发污染
 * - 仅修改 chat/completions 请求，不影响 embedding 等其他调用
 * - 失败安全：JSON 解析失败时降级为原始请求
 */
public class DeepSeekThinkingInterceptor implements ClientHttpRequestInterceptor {

    private static final ThreadLocal<Boolean> THINKING_ENABLED = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void setThinkingEnabled(boolean enabled) {
        THINKING_ENABLED.set(enabled);
    }

    public static void clear() {
        THINKING_ENABLED.remove();
    }

    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body,
                                         @NonNull ClientHttpRequestExecution execution) throws IOException {
        if (body == null || body.length == 0) {
            return execution.execute(request, body);
        }

        String uri = request.getURI().toString();
        if (!uri.contains("/chat/completions")) {
            return execution.execute(request, body);
        }

        try {
            String jsonBody = new String(body, StandardCharsets.UTF_8);
            ObjectNode root = (ObjectNode) objectMapper.readTree(jsonBody);

            // DeepSeek v4 默认思考模式为 enabled，必须显式设置
            ObjectNode thinking = objectMapper.createObjectNode();
            thinking.put("type", THINKING_ENABLED.get() ? "enabled" : "disabled");
            root.set("thinking", thinking);

            byte[] modifiedBody = objectMapper.writeValueAsBytes(root);
            return execution.execute(request, modifiedBody);
        } catch (Exception e) {
            return execution.execute(request, body);
        }
    }
}
