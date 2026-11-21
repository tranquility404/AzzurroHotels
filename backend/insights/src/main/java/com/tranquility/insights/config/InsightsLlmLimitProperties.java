package com.tranquility.insights.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "insights.llm.limits")
public record InsightsLlmLimitProperties(long maxCalls, int maxPromptTokens) {

    public InsightsLlmLimitProperties {
        if (maxCalls < 0) {
            throw new IllegalArgumentException("The insights LLM max-calls limit cannot be negative.");
        }
        if (maxPromptTokens < 1) {
            throw new IllegalArgumentException("The insights LLM max-prompt-tokens limit must be positive.");
        }
    }
}
