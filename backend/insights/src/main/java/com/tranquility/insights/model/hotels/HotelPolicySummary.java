package com.tranquility.insights.model.hotels;

public record HotelPolicySummary(
        String hotelName,
        String checkinFrom,
        String checkinUntil,
        String checkoutFrom,
        String checkoutUntil,
        String pets,
        String allowsChildren,
        String minimumChildAge,
        String cribsAvailable,
        String extraBedsAvailable,
        String acceptedPayments,
        String finePrint
) {
}
