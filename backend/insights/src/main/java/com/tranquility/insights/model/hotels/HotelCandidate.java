package com.tranquility.insights.model.hotels;

public record HotelCandidate(
        String hotelId,
        String hotelName,
        String location,
        String overallRating,
        String ratingCount,
        long reviewCount,
        String nearbyMatches
) {
}
