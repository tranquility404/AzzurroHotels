package com.tranquility.insights.model.hotels;

public record HotelLocation(
        String hotelId,
        String hotelName,
        String address,
        String city,
        String region,
        String country,
        String postalCode,
        String latitude,
        String longitude
) {
}
