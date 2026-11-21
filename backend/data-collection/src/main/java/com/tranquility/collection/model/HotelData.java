package com.tranquility.collection.model;

import tools.jackson.databind.JsonNode;

public record HotelData(
        String requestedUrl,
        String hotelId,
        String hotelName,
        JsonNode hotelDetails,
        int reviewCount
) {
}
