package com.tranquility.dashboard.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record HotelSyncBatchRequest(
        @NotNull UUID continuationToken,
        @NotNull UUID batchId
) {
}
