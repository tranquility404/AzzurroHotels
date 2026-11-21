package com.tranquility.insights.exception;

public class LlmPromptTooLargeException extends LlmRequestLimitExceededException {

    public LlmPromptTooLargeException(int tokenCount, int maxPromptTokens) {
        super("This LLM request contains " + tokenCount + " prompt tokens; the limit is "
                + maxPromptTokens + " tokens.");
    }
}
