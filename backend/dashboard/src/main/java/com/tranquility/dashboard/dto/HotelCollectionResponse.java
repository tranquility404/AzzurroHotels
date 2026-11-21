package com.tranquility.dashboard.dto;

import java.util.List;

public record HotelCollectionResponse(
        List<HotelCollectionItemResponse> properties,
        int successCount,
        int failureCount,
        int skippedCount
) {
    public HotelCollectionResponse {
        properties = List.copyOf(properties);
    }
}
