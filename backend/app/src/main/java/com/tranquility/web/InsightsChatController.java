package com.tranquility.web;

import com.tranquility.insights.agent.InsightsAgentService;
import com.tranquility.insights.dto.InsightsChatHistoryResponse;
import com.tranquility.insights.dto.InsightsChatRequest;
import com.tranquility.insights.dto.InsightsChatResponse;
import com.tranquility.insights.agent.InsightsChatStreamEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api/insights/chat")
@RequiredArgsConstructor
public class InsightsChatController {

    private final InsightsAgentService insightsAgentService;

    @PostMapping
    public InsightsChatResponse chat(@Valid @RequestBody InsightsChatRequest request) {
        String answer = insightsAgentService.ask(request.conversationId(), request.message());
        return new InsightsChatResponse(request.conversationId(), answer);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<InsightsChatStreamEvent>> stream(
            @Valid @RequestBody InsightsChatRequest request
    ) {
        return insightsAgentService.stream(request.conversationId(), request.message())
                .map(event -> ServerSentEvent.builder(event).event(event.type()).build());
    }

    @GetMapping("/{conversationId}")
    public InsightsChatHistoryResponse history(@PathVariable UUID conversationId) {
        return InsightsChatHistoryResponse.from(conversationId, insightsAgentService.history(conversationId));
    }
}
