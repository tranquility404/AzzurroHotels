package com.tranquility.insights.dto;

import com.tranquility.insights.agent.InsightsConversationMessage;

import java.util.List;
import java.util.UUID;

public record InsightsChatHistoryResponse(UUID conversationId, List<Message> messages) {

    public static InsightsChatHistoryResponse from(
            UUID conversationId,
            List<InsightsConversationMessage> messages
    ) {
        return new InsightsChatHistoryResponse(conversationId, messages.stream()
                .map(message -> new Message(message.role(), message.content(), message.toolCalls()))
                .toList());
    }

    public record Message(String role, String content, List<String> toolCalls) {
    }
}
