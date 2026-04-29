package com.interview.agent.service.rag.rewrite;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.query-rewrite")
public class QueryRewriteProperties {

    private boolean enabled = true;

    private int minQueryLength = 10;

    private int maxQueryLength = 500;

    private long timeoutMs = 30000;

    private boolean needRewriteWhenShort = true;

    private boolean needRewriteWhenColloquial = true;

    private boolean needRewriteWhenHasPronoun = true;

    private String colloquialPattern = ".*(啥|怎么|怎样|咋|咋办|啥意思|是什么意思|告诉我|教我|给我讲).*";

    private String pronounPattern = ".*(它|这个|那个|上面|前文|之前|刚才|接着).*";
}