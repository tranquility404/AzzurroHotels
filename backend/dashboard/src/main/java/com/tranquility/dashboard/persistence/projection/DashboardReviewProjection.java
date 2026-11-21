package com.tranquility.dashboard.persistence.projection;

import java.util.UUID;

public interface DashboardReviewProjection {
    UUID getId();

    String getHotelId();

    String getHotelName();

    String getCity();

    String getCountry();

    String getReviewDate();

    Integer getScore();

    String getSourceDataJson();
}
