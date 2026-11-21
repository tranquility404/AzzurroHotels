package com.tranquility.insights.agent;

import com.tranquility.insights.exception.InsightsAgentUnavailableException;
import com.tranquility.insights.exception.LlmRequestLimitExceededException;
import com.tranquility.insights.advisor.ToolCallStreamingAdvisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class InsightsAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InsightsAgentService.class);

    private final ChatClient chatClient;
    private final SessionService sessionService;

    public InsightsAgentService(ChatClient insightsChatClient, SessionService sessionService) {
        this.chatClient = insightsChatClient;
        this.sessionService = sessionService;
    }

    public String ask(UUID conversationId, String message) {
        try {
            String answer = chatClient.prompt()
                    .user(message.trim())
                    .advisors(SessionAdvisors.session(conversationId.toString()))
                    .call()
                    .content();
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("The model returned an empty answer.");
            }
            return answer;
        } catch (LlmRequestLimitExceededException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn("Hotel insights chat failed for conversation {}", conversationId, exception);
            throw new InsightsAgentUnavailableException("The guest insights assistant is temporarily unavailable.", exception);
        }
    }

    public Flux<InsightsChatStreamEvent> stream(UUID conversationId, String message) {
        return Flux.create(sink -> {
            Object eventLock = new Object();
            StringBuilder streamedAnswer = new StringBuilder();

            try {
                var contentStream = chatClient.prompt()
                        .user(message.trim())
                        .advisors(advisorSpec -> {
                            SessionAdvisors.session(conversationId.toString()).accept(advisorSpec);
                            advisorSpec.advisors(new ToolCallStreamingAdvisor(toolName -> emit(
                                    sink, eventLock, new InsightsChatStreamEvent("tool", toolName))));
                        })
                        .stream()
                        .content();

                var subscription = contentStream.subscribe(
                        delta -> {
                            streamedAnswer.append(delta);
                            emit(sink, eventLock, new InsightsChatStreamEvent("token", delta));
                        },
                        exception -> {
                            Throwable cause = Exceptions.unwrap(exception);
                            LOGGER.warn("Hotel insights stream failed for conversation {} ({})",
                                    conversationId, cause.getClass().getSimpleName());
                            String errorMessage = cause instanceof LlmRequestLimitExceededException limitException
                                    ? limitException.getMessage()
                                    : "The guest insights assistant is temporarily unavailable. Please try again.";
                            complete(sink, eventLock, new InsightsChatStreamEvent(
                                    "error", errorMessage));
                        },
                        () -> {
                            InsightsChatStreamEvent terminalEvent = streamedAnswer.isEmpty()
                                    ? new InsightsChatStreamEvent("error", "The assistant returned an empty answer. Please try again.")
                                    : new InsightsChatStreamEvent("done", "");
                            complete(sink, eventLock, terminalEvent);
                        });
                sink.onCancel(subscription::dispose);
            } catch (RuntimeException exception) {
                LOGGER.warn("Hotel insights stream could not start for conversation {} ({})",
                        conversationId, exception.getClass().getSimpleName());
                complete(sink, eventLock, new InsightsChatStreamEvent(
                        "error", "The guest insights assistant is temporarily unavailable. Please try again."));
            }
        });
    }

    private static void emit(FluxSink<InsightsChatStreamEvent> sink, Object eventLock,
                             InsightsChatStreamEvent event) {
        synchronized (eventLock) {
            if (!sink.isCancelled()) {
                sink.next(event);
            }
        }
    }

    private static void complete(FluxSink<InsightsChatStreamEvent> sink, Object eventLock,
                                 InsightsChatStreamEvent terminalEvent) {
        synchronized (eventLock) {
            if (!sink.isCancelled()) {
                sink.next(terminalEvent);
                sink.complete();
            }
        }
    }

    public List<InsightsConversationMessage> history(UUID conversationId) {
        return sessionService.getActiveMessages(conversationId.toString()).stream()
                .map(InsightsAgentService::toConversationMessage)
                .filter(message -> message != null
                        && (!message.content().isBlank() || !message.toolCalls().isEmpty()))
                .toList();
    }

    static InsightsConversationMessage toConversationMessage(Message message) {
        if (message instanceof UserMessage) {
            return new InsightsConversationMessage("user", safeText(message.getText()), List.of());
        }
        if (message instanceof AssistantMessage assistantMessage) {
            var savedToolCalls = assistantMessage.getToolCalls();
            List<String> toolCalls = (savedToolCalls == null ? List.<AssistantMessage.ToolCall>of() : savedToolCalls).stream()
                    .filter(Objects::nonNull)
                    .map(toolCall -> ToolCallStreamingAdvisor.formatToolName(toolCall.name()))
                    .filter(Objects::nonNull)
                    .toList();
            return new InsightsConversationMessage("assistant", safeText(assistantMessage.getText()), toolCalls);
        }
        return null;
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }
}
