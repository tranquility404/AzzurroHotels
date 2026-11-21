package com.tranquility.dashboard.exception;

public class HotelReviewSearchException extends RuntimeException {

    public HotelReviewSearchException(String message) {
        super(message);
    }

    public HotelReviewSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
