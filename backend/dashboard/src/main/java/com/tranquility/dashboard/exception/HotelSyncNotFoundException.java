package com.tranquility.dashboard.exception;

public class HotelSyncNotFoundException extends RuntimeException {

    public HotelSyncNotFoundException(String hotelId) {
        super("Hotel '" + hotelId + "' was not found.");
    }
}
