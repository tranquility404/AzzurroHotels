package com.tranquility.insights.model.analytics;

import java.time.LocalDate;

public record ReviewExcerpt(
        String reviewId,
        String hotelName,
        LocalDate reviewDate,
        int score,
        String reviewerName,
        String text
) {
}
