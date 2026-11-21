package com.tranquility.insights.dto;

import java.util.UUID;

public record InsightsChatResponse(UUID conversationId, String answer) {
}
