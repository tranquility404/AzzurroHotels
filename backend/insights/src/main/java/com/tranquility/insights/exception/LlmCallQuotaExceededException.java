package com.tranquility.insights.exception;

public class LlmCallQuotaExceededException extends LlmRequestLimitExceededException {

    public LlmCallQuotaExceededException(long maxCalls) {
        super("The guest insights assistant has reached its lifetime limit of " + maxCalls + " LLM calls.");
    }
}
