package com.tranquility.dashboard.model;

public enum HotelSyncStatus {
    IDLE,
    SYNCING,
    AWAITING_CONFIRMATION,
    AWAITING_CONTINUATION,
    COMPLETE,
    FAILED,
    CANCELLED
}
