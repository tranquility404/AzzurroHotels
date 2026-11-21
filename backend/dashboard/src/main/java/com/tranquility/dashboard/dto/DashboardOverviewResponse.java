package com.tranquility.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardOverviewResponse(
        LocalDate asOfDate,
        Period period,
        List<Property> properties,
        Stats current,
        Stats previous,
        Deltas deltas,
        int reportingProperties,
        List<PropertyPerformance> propertyPerformance,
        Trend trend,
        List<OperationalTopic> operationalTopics
) {
    public record Period(
            String preset,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate previousStartDate,
            LocalDate previousEndDate,
            int dayCount,
            String comparisonLabel
    ) {
    }

    public record Property(String id, String name, String city, String country) {
    }

    public record Stats(
            long count,
            Double average,
            long positiveCount,
            long neutralCount,
            long negativeCount,
            double positiveShare,
            long replyCount,
            double replyRate
    ) {
    }

    public record Deltas(
            Double averageRating,
            Double reviewCountPercent,
            Double positiveSharePoints,
            Double replyRatePoints
    ) {
    }

    public record PropertyPerformance(
            Property property,
            long reviewCount,
            Double average,
            Double delta,
            long previousReviewCount
    ) {
    }

    public record Trend(
            List<TrendPoint> points,
            long positiveTotal,
            long negativeTotal,
            Double positiveVsNegativePercent,
            double averagePositivePerWeek,
            String bestWeekLabel,
            String accessibleSummary,
            List<TrendDay> dailyPoints
    ) {
    }

    /** Score counts are indexed by score minus one (index 0 is score 1). */
    public record TrendDay(LocalDate date, List<Long> scoreCounts, long total) {
    }

    public record TrendPoint(
            LocalDate startDate,
            LocalDate endDate,
            String label,
            long positive,
            long neutral,
            long negative,
            long total
    ) {
    }

    public record OperationalTopic(
            String topic,
            long reviewCount,
            long positiveReviewCount,
            long neutralReviewCount,
            long negativeReviewCount,
            Double positiveReviewSharePercent,
            Double negativeReviewSharePercent
    ) {
    }
}
