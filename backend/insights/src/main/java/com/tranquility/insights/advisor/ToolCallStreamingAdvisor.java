package com.tranquility.insights.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Observes completed tool requests inside the ChatClient's tool-calling loop. */
public final class ToolCallStreamingAdvisor implements StreamAdvisor {

    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
            Map.entry("find_hotels", "findHotels"),
            Map.entry("summarize_performance", "summarizePerformance"),
            Map.entry("summarize_portfolio_performance", "summarizePortfolioPerformance"),
            Map.entry("compare_topic_trends", "compareTopicTrends"),
            Map.entry("find_review_evidence", "findReviewEvidence"),
            Map.entry("get_hotel_location", "getHotelLocation"),
            Map.entry("get_hotel_location_inventory", "getHotelLocationInventory"),
            Map.entry("get_hotel_nearby_places", "getHotelNearbyPlaces"),
            Map.entry("get_hotel_facilities", "getHotelFacilities"),
            Map.entry("get_hotel_rooms", "getHotelRooms"),
            Map.entry("get_hotel_policies", "getHotelPolicies"),
            Map.entry("get_hotel_ratings", "getHotelRatings"),
            Map.entry("get_hotel_meals", "getHotelMeals"),
            Map.entry("get_current_time", "getCurrentTime"));
    private final Consumer<String> onToolCall;
    private final Set<String> seenToolCallIds = ConcurrentHashMap.newKeySet();

    public ToolCallStreamingAdvisor(Consumer<String> onToolCall) {
        this.onToolCall = onToolCall;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(request).doOnNext(this::publishToolCalls);
    }

    private void publishToolCalls(ChatClientResponse response) {
        if (response.chatResponse() == null || response.chatResponse().getResults() == null) {
            return;
        }

        for (Generation generation : response.chatResponse().getResults()) {
            if (!(generation.getOutput() instanceof AssistantMessage assistantMessage)) {
                continue;
            }
            var toolCalls = assistantMessage.getToolCalls();
            if (toolCalls == null) {
                continue;
            }
            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                if (toolCall == null) {
                    continue;
                }
                String toolName = toolCall.name();
                String displayName = formatToolName(toolName);
                if (displayName == null) {
                    continue;
                }
                String dedupeKey = toolCall.id() == null || toolCall.id().isBlank()
                        ? toolName + ":" + toolCall.arguments()
                        : toolCall.id();
                if (seenToolCallIds.add(dedupeKey)) {
                    onToolCall.accept(displayName);
                }
            }
        }
    }

    public static String formatToolName(String toolName) {
        return toolName == null ? null : DISPLAY_NAMES.get(toolName);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 400;
    }

    @Override
    public String getName() {
        return "ToolCallStreamingAdvisor";
    }
}
