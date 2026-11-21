package com.tranquility.insights.model.analytics;

import java.time.LocalDate;

public record ReviewPeriod(
        String key,
        LocalDate asOfDate,
        LocalDate currentStart,
        LocalDate currentEnd,
        LocalDate previousStart,
        LocalDate previousEnd,
        String currentLabel,
        String previousLabel
) {
}
