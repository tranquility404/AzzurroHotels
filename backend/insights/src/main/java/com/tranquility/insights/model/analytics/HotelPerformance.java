package com.tranquility.insights.model.analytics;

public record HotelPerformance(
        String hotelId,
        String hotelName,
        long currentReviewCount,
        long currentScoreTotal,
        Double currentAverageScore,
        long currentPositiveCount,
        long currentNeutralCount,
        long currentNegativeCount,
        long currentReplyCount,
        long previousReviewCount,
        long previousScoreTotal,
        Double previousAverageScore,
        long previousPositiveCount,
        long previousNeutralCount,
        long previousNegativeCount,
        long previousReplyCount
) {
}
