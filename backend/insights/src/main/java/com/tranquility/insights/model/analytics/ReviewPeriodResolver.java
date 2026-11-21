package com.tranquility.insights.model.analytics;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReviewPeriodResolver {

    private static final Pattern CUSTOM_DATE_RANGE = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})\\s+to\\s+(\\d{4}-\\d{2}-\\d{2})$",
            Pattern.CASE_INSENSITIVE);
    private static final long MAX_CUSTOM_RANGE_DAYS = 366;

    public ReviewPeriod resolve(String requestedPeriod, LocalDate asOfDate) {
        if (asOfDate == null) {
            throw new IllegalArgumentException("A data reference date is required to resolve the review period.");
        }
        if (requestedPeriod == null || requestedPeriod.isBlank()) {
            throw new IllegalArgumentException("Specify a review period, such as this_week, last_week, this_month, or YYYY-MM-DD to YYYY-MM-DD.");
        }
        ReviewPeriod customPeriod = resolveCustomRange(requestedPeriod.trim(), asOfDate);
        if (customPeriod != null) return customPeriod;

        String key = normalize(requestedPeriod);
        LocalDate currentStart;
        LocalDate currentEnd = asOfDate;
        LocalDate previousStart;
        LocalDate previousEnd;
        String currentLabel;
        String previousLabel;

        switch (key) {
            case "this_week" -> {
                currentStart = asOfDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                previousStart = currentStart.minusWeeks(1);
                previousEnd = currentEnd.minusWeeks(1);
                currentLabel = "this week to date";
                previousLabel = "the same days of last week";
            }
            case "last_week" -> {
                currentEnd = asOfDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1);
                currentStart = currentEnd.minusDays(6);
                previousEnd = currentStart.minusDays(1);
                previousStart = previousEnd.minusDays(6);
                currentLabel = "last complete calendar week";
                previousLabel = "the calendar week before that";
            }
            case "last_two_weeks" -> {
                currentStart = asOfDate.minusDays(13);
                previousEnd = currentStart.minusDays(1);
                previousStart = previousEnd.minusDays(13);
                currentLabel = "the last 14 days";
                previousLabel = "the 14 days before that";
            }
            case "this_month" -> {
                currentStart = asOfDate.withDayOfMonth(1);
                YearMonth previousMonth = YearMonth.from(asOfDate).minusMonths(1);
                previousStart = previousMonth.atDay(1);
                previousEnd = previousMonth.atDay(Math.min(asOfDate.getDayOfMonth(), previousMonth.lengthOfMonth()));
                currentLabel = "this month to date";
                previousLabel = "the same calendar dates last month";
            }
            case "last_month" -> {
                YearMonth lastMonth = YearMonth.from(asOfDate).minusMonths(1);
                YearMonth monthBefore = lastMonth.minusMonths(1);
                currentStart = lastMonth.atDay(1);
                currentEnd = lastMonth.atEndOfMonth();
                previousStart = monthBefore.atDay(1);
                previousEnd = monthBefore.atEndOfMonth();
                currentLabel = "last calendar month";
                previousLabel = "the calendar month before that";
            }
            case "last_30_days" -> {
                currentStart = asOfDate.minusDays(29);
                previousEnd = currentStart.minusDays(1);
                previousStart = previousEnd.minusDays(29);
                currentLabel = "the last 30 days";
                previousLabel = "the 30 days before that";
            }
            case "last_90_days" -> {
                currentStart = asOfDate.minusDays(89);
                previousEnd = currentStart.minusDays(1);
                previousStart = previousEnd.minusDays(89);
                currentLabel = "the last 90 days";
                previousLabel = "the 90 days before that";
            }
            default -> throw new IllegalArgumentException(
                    "Use this_week, last_week, last_two_weeks, this_month, last_month, last_30_days, last_90_days, or YYYY-MM-DD to YYYY-MM-DD.");
        }

        return new ReviewPeriod(key, asOfDate, currentStart, currentEnd,
                previousStart, previousEnd, currentLabel, previousLabel);
    }

    private static String normalize(String requestedPeriod) {
        String value = requestedPeriod.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (value) {
            case "week", "this_week_to_date", "week_to_date" -> "this_week";
            case "previous_week", "previous_complete_week" -> "last_week";
            case "two_weeks", "last_14_days", "14_days", "14d" -> "last_two_weeks";
            case "month", "month_to_date" -> "this_month";
            case "previous_month", "prior_month" -> "last_month";
            case "30_days", "30d", "last_month_rolling" -> "last_30_days";
            case "90_days", "90d" -> "last_90_days";
            default -> value;
        };
    }

    private static ReviewPeriod resolveCustomRange(String requestedPeriod, LocalDate asOfDate) {
        Matcher matcher = CUSTOM_DATE_RANGE.matcher(requestedPeriod.trim());
        if (!matcher.matches()) return null;

        LocalDate currentStart;
        LocalDate currentEnd;
        try {
            currentStart = LocalDate.parse(matcher.group(1));
            currentEnd = LocalDate.parse(matcher.group(2));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Custom dates must be valid YYYY-MM-DD dates.", exception);
        }
        if (currentEnd.isBefore(currentStart)) {
            throw new IllegalArgumentException("The end date must be on or after the start date.");
        }
        if (currentEnd.isAfter(asOfDate)) {
            throw new IllegalArgumentException("The requested range ends after the latest review date in the data (" + asOfDate + ").");
        }

        long days = ChronoUnit.DAYS.between(currentStart, currentEnd) + 1;
        if (days > MAX_CUSTOM_RANGE_DAYS) {
            throw new IllegalArgumentException("Custom review ranges are limited to 366 days.");
        }
        LocalDate previousEnd = currentStart.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(days - 1);
        return new ReviewPeriod("custom_date_range", asOfDate, currentStart, currentEnd,
                previousStart, previousEnd, "the requested custom date range", "the immediately preceding equal-length range");
    }
}
