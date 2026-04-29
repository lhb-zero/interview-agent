package com.interview.agent.service.rag.hybrid;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.hybrid-search")
public class HybridSearchProperties {

    private boolean enabled = false;

    private int vectorWeight = 40;

    private int keywordWeight = 60;

    private int topK = 20;

    private double rrfK = 60.0;

    private boolean keywordSearchEnabled = true;

    private String keywordMatchMode = "contains";
}