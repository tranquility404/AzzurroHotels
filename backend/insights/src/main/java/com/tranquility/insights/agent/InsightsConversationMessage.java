package com.tranquility.insights.agent;

import java.util.List;

public record InsightsConversationMessage(String role, String content, List<String> toolCalls) {
    public InsightsConversationMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
