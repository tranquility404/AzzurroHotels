package com.tranquility.dashboard.exception;

public class InvalidDashboardFilterException extends RuntimeException {
    public InvalidDashboardFilterException(String message) {
        super(message);
    }

    public InvalidDashboardFilterException(String message, Throwable cause) {
        super(message, cause);
    }
}
