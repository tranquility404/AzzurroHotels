package com.tranquility.dashboard.exception;

import java.time.Instant;

public class HotelSyncProviderException extends RuntimeException {

    private final Instant nextAllowedAt;

    public HotelSyncProviderException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public HotelSyncProviderException(String message, Throwable cause, Instant nextAllowedAt) {
        super(message, cause);
        this.nextAllowedAt = nextAllowedAt;
    }

    public Instant getNextAllowedAt() {
        return nextAllowedAt;
    }
}
