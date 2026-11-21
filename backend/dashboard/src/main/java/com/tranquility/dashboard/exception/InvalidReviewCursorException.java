package com.tranquility.dashboard.exception;

public class InvalidReviewCursorException extends RuntimeException {

    public InvalidReviewCursorException() {
        super("The review cursor is invalid.");
    }

    public InvalidReviewCursorException(Throwable cause) {
        super("The review cursor is invalid.", cause);
    }
}
