package com.tranquility.insights.model.analytics;

public record TopicTrend(
        String topic,
        long currentPositiveCount,
        long currentNegativeCount,
        long previousPositiveCount,
        long previousNegativeCount,
        Double currentPositiveSharePercent,
        Double currentNegativeSharePercent,
        Double previousPositiveSharePercent,
        Double previousNegativeSharePercent
) {
}
