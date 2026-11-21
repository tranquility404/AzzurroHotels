package com.tranquility.collection;

import com.tranquility.collection.model.HotelReviewPage;

public interface HotelReviewPageCollector {

    HotelReviewPage collectReviews(String hotelId, int pageNumber);
}
