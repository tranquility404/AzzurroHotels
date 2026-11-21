package com.tranquility.dashboard.dto;

public record HotelSummaryResponse(
        String hotelId,
        String hotelName,
        Double rating,
        long reviewCount,
        String collectedAt,
        String fileName
) {
}
