package com.tranquility.dashboard.model;

import tools.jackson.databind.JsonNode;

public record HotelSyncReviewPreview(
        int sourcePosition,
        String sourceReviewId,
        String classification,
        JsonNode review
) {
}
