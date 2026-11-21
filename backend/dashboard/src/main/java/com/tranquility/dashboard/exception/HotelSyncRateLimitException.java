package com.tranquility.dashboard.exception;

import java.time.Instant;

public class HotelSyncRateLimitException extends RuntimeException {

    private final Instant nextAllowedAt;

    public HotelSyncRateLimitException(Instant nextAllowedAt) {
        super("This hotel has already been synced today.");
        this.nextAllowedAt = nextAllowedAt;
    }

    public Instant getNextAllowedAt() {
        return nextAllowedAt;
    }
}
