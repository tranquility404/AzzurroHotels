package com.tranquility.dashboard.dto;

import java.util.List;

public record DashboardReviewPageResponse(
        List<Review> data,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public record Review(
            String id,
            String propertyId,
            String propertyName,
            String propertyCity,
            int score,
            String sentiment,
            String title,
            String date,
            String positive,
            String negative,
            String guestName,
            String guestCountry,
            String guestType,
            String roomName,
            Integer nights,
            String language,
            String ownerReply,
            List<String> positiveTopics,
            List<String> negativeTopics
    ) {
    }
}
