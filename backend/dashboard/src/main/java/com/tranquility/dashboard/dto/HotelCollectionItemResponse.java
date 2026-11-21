package com.tranquility.dashboard.dto;

public record HotelCollectionItemResponse(
        String status,
        String bookingUrl,
        String hotelId,
        String hotelName,
        Double rating,
        long reviewCount,
        int reviewPages,
        String fileName,
        String message
) {

    public static HotelCollectionItemResponse failed(String bookingUrl, String message) {
        return new HotelCollectionItemResponse("FAILED", bookingUrl, null, null, null, 0, 0, null, message);
    }

    public static HotelCollectionItemResponse skipped(String bookingUrl) {
        return new HotelCollectionItemResponse("SKIPPED", bookingUrl, null, null, null, 0, 0, null,
                "This Booking.com property is already in the submission.");
    }
}
