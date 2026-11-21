package com.tranquility.dashboard.model;

public record ReviewCursor(int sourcePageNumber, int position) {

    public ReviewCursor {
        if (position < 0) {
            throw new IllegalArgumentException("A review cursor must contain a nonnegative position.");
        }
    }
}
