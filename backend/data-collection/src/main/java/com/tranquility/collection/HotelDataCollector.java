package com.tranquility.collection;

import com.tranquility.collection.model.HotelData;

public interface HotelDataCollector {

    HotelData collect(String bookingHotelUrl);
}
