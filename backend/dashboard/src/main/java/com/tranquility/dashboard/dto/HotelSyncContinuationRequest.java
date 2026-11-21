package com.tranquility.dashboard.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record HotelSyncContinuationRequest(@NotNull UUID continuationToken) {
}
