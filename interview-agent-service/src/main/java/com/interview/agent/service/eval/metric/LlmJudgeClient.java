package com.interview.agent.service.eval.metric;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.service.eval.EvalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 评审客户端 — 封装 OllamaChatModel 用于评估指标计算
 */
@Slf4j
@Component
public class LlmJudgeClient {

    private final ChatModel chatModel;
    private final EvalProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmJudgeClient(ChatModel chatModel, EvalProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    /**
     * 调用 LLM Judge 获取评审结果
     */
    public String judge(String systemPrompt, String userPrompt) {
        EvalProperties.JudgeModel judgeConfig = properties.getJudge();
        for (int attempt = 0; attempt <= judgeConfig.getMaxRetries(); attempt++) {
            try {
                OllamaChatOptions options = OllamaChatOptions.builder()
                        .model(judgeConfig.getModel())
                        .temperature(judgeConfig.getTemperature())
                        .numCtx(4096)
                        .build();
                Prompt prompt = new Prompt(List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userPrompt)
                ), options);
                String response = chatModel.call(prompt).getResult().getOutput().getText();
                if (response != null) {
                    return response.trim();
                }
            } catch (Exception e) {
                log.warn("[Eval-Judge] LLM 调用失败 (attempt {}/{}): {}",
                        attempt + 1, judgeConfig.getMaxRetries() + 1, e.getMessage());
                if (attempt == judgeConfig.getMaxRetries()) {
                    throw new RuntimeException("LLM Judge 调用失败: " + e.getMessage(), e);
                }
            }
        }
        return "";
    }

    /**
     * 调用 LLM Judge 并提取 YES/NO 判断
     * @return true=YES, false=NO
     */
    public boolean judgeYesNo(String systemPrompt, String userPrompt) {
        String response = judge(systemPrompt, userPrompt);
        String upper = response.toUpperCase();
        // 优先匹配开头的 YES/NO
        if (upper.startsWith("YES") || upper.startsWith("是")) return true;
        if (upper.startsWith("NO") || upper.startsWith("否")) return false;
        // 包含匹配
        if (upper.contains("YES") || upper.contains("是") || upper.contains("SUPPORTED")) return true;
        if (upper.contains("NO") || upper.contains("否") || upper.contains("NOT_SUPPORTED")) return false;
        log.warn("[Eval-Judge] 无法解析 YES/NO 响应: {}", response);
        return false;
    }

    /**
     * 调用 LLM Judge 并解析 JSON 数组响应
     * 兼容两种格式：字符串数组 ["q1","q2"] 或对象数组 [{"question":"q1"},...]
     */
    public List<String> judgeJsonArray(String systemPrompt, String userPrompt) {
        String response = judge(systemPrompt, userPrompt);
        try {
            String json = extractJsonArray(response);
            // 先尝试解析为字符串数组
            try {
                return objectMapper.readValue(json, new TypeReference<List<String>>() {});
            } catch (Exception ignored) {}
            // 如果失败，尝试解析为对象数组并提取 "question" 字段
            List<Map<String, Object>> objects = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<String> result = new ArrayList<>();
            for (Map<String, Object> obj : objects) {
                Object q = obj.get("question");
                if (q != null) result.add(q.toString());
            }
            return result;
        } catch (Exception e) {
            log.warn("[Eval-Judge] JSON 解析失败: {}, response: {}", e.getMessage(), response);
            return Collections.emptyList();
        }
    }

    /**
     * 从响应中提取 JSON 数组
     */
    private String extractJsonArray(String response) {
        // 先尝试直接解析
        String trimmed = response.trim();
        if (trimmed.startsWith("[")) return trimmed;
        // 尝试从 markdown 代码块中提取（贪婪匹配整个数组）
        Pattern pattern = Pattern.compile("```(?:json)?\\s*\\n?(\\[.*])\\s*```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) return matcher.group(1);
        // 尝试找第一个 [ 到最后一个 ]
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start >= 0 && end > start) return response.substring(start, end + 1);
        return response;
    }
}
