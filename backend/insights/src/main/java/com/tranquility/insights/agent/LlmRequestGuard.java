package com.tranquility.insights.agent;

import com.tranquility.insights.config.InsightsLlmLimitProperties;
import com.tranquility.insights.exception.LlmCallQuotaExceededException;
import com.tranquility.insights.exception.LlmPromptTooLargeException;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class LlmRequestGuard {

    private final InsightsLlmLimitProperties limits;
    private final TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();
    private final AtomicLong callsUsed = new AtomicLong();

    public LlmRequestGuard(InsightsLlmLimitProperties limits) {
        this.limits = limits;
    }

    public void authorize(Prompt prompt) {
        int promptTokens = tokenCountEstimator.estimate(prompt.getInstructions().toString());
        if (promptTokens > limits.maxPromptTokens()) {
            throw new LlmPromptTooLargeException(promptTokens, limits.maxPromptTokens());
        }
        if (!tryConsumeCall()) {
            throw new LlmCallQuotaExceededException(limits.maxCalls());
        }
    }

    private boolean tryConsumeCall() {
        while (true) {
            long used = callsUsed.get();
            if (used >= limits.maxCalls()) {
                return false;
            }
            if (callsUsed.compareAndSet(used, used + 1)) {
                return true;
            }
        }
    }
}
