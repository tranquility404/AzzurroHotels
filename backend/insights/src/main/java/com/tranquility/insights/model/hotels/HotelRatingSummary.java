package com.tranquility.insights.model.hotels;

public record HotelRatingSummary(
        String hotelName,
        String overallScore,
        String reviewCount,
        String starRating,
        String categoryRatings
) {
}
