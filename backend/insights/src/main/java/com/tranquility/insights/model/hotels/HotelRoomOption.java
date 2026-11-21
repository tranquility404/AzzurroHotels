package com.tranquility.insights.model.hotels;

public record HotelRoomOption(
        String name,
        String description,
        String sizeSquareMeters,
        String maxOccupancy,
        String maxChildren,
        String beds,
        String facilityHighlights
) {
}
