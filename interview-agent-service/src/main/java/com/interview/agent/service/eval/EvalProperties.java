package com.interview.agent.service.eval;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 评估配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.eval")
public class EvalProperties {

    private boolean enabled = true;

    private JudgeModel judge = new JudgeModel();

    private Metrics metrics = new Metrics();

    private Execution execution = new Execution();

    @Data
    public static class JudgeModel {
        /** 评审模型名称 */
        private String model = "qwen3:1.7b";
        /** 温度（低温度更确定性） */
        private double temperature = 0.1;
        /** 最大重试次数 */
        private int maxRetries = 2;
        /** 超时毫秒 */
        private long timeoutMs = 60000;
    }

    @Data
    public static class Metrics {
        private boolean contextPrecisionEnabled = true;
        private boolean contextRecallEnabled = true;
        private boolean faithfulnessEnabled = true;
        private boolean answerRelevancyEnabled = true;
        /** Answer Relevancy 反推问题数量 */
        private int relevancySyntheticQuestions = 5;
    }

    @Data
    public static class Execution {
        /** 并发数（1=顺序执行） */
        private int concurrency = 1;
        /** 批次大小 */
        private int batchSize = 10;
    }
}
