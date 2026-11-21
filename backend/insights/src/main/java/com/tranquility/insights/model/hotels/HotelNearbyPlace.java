package com.tranquility.insights.model.hotels;

public record HotelNearbyPlace(
        String category,
        String name,
        String type,
        String distance,
        String walkTime,
        String driveTime
) {
}
