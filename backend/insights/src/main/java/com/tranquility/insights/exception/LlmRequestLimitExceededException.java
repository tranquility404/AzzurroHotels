package com.tranquility.insights.exception;

public abstract class LlmRequestLimitExceededException extends RuntimeException {

    protected LlmRequestLimitExceededException(String message) {
        super(message);
    }
}
