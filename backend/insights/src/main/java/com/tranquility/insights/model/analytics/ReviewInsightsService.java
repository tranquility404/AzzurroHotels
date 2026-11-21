package com.tranquility.insights.model.analytics;

import com.tranquility.insights.persistence.repository.ReviewInsightsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ReviewInsightsService {

    private static final String HOTEL_ID_PATTERN = "[A-Za-z0-9_-]{1,128}";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<![\\p{L}\\p{N}])\\+?\\d(?:[\\d().\\s-]{6,}\\d)(?![\\p{L}\\p{N}])");
    private static final int MAX_REVIEWER_NAME_LENGTH = 100;

    private final ReviewInsightsRepository repository;
    private final ReviewPeriodResolver periodResolver;
    private final Clock clock;

    public ReviewInsightsService(ReviewInsightsRepository repository, ReviewPeriodResolver periodResolver, Clock clock) {
        this.repository = repository;
        this.periodResolver = periodResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public String summarizePerformance(String requestedHotelId, String requestedPeriod) {
        String hotelId;
        try {
            hotelId = normalizeHotelId(requestedHotelId);
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }

        ReviewPeriod period;
        try {
            period = resolvePeriod(requestedPeriod);
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }

        HotelPerformance hotel = repository.findHotelPerformance(period, hotelId).stream().findFirst().orElse(null);
        if (hotel == null || hotel.currentReviewCount() == 0 && hotel.previousReviewCount() == 0) {
            return "No scored reviews were found for this hotel in the requested period or its comparison window. "
                    + periodContext(period);
        }

        StringBuilder result = new StringBuilder(periodContext(period)).append('\n')
                .append(hotel.hotelName()).append(" (hotel id ").append(hotel.hotelId()).append(")\n")
                .append("Current: ").append(metrics(hotel.currentReviewCount(), hotel.currentAverageScore(),
                        hotel.currentPositiveCount(), hotel.currentNeutralCount(), hotel.currentNegativeCount(),
                        hotel.currentReplyCount()))
                .append("\nComparison: ").append(metrics(hotel.previousReviewCount(), hotel.previousAverageScore(),
                        hotel.previousPositiveCount(), hotel.previousNeutralCount(), hotel.previousNegativeCount(),
                        hotel.previousReplyCount()));

        if (hotel.currentAverageScore() != null && hotel.previousAverageScore() != null) {
            result.append("\nAverage-score change: ")
                    .append(signed(hotel.currentAverageScore() - hotel.previousAverageScore()))
                    .append(" points on the 10-point scale.");
        }
        if (hotel.currentReviewCount() > 0 && hotel.previousReviewCount() > 0) {
            double currentPositiveShare = percent(hotel.currentPositiveCount(), hotel.currentReviewCount());
            double previousPositiveShare = percent(hotel.previousPositiveCount(), hotel.previousReviewCount());
            double currentNegativeShare = percent(hotel.currentNegativeCount(), hotel.currentReviewCount());
            double previousNegativeShare = percent(hotel.previousNegativeCount(), hotel.previousReviewCount());
            result.append("\nPositive-review share: ").append(format(currentPositiveShare)).append("% vs ")
                    .append(format(previousPositiveShare)).append("% (")
                    .append(signed(currentPositiveShare - previousPositiveShare)).append(" percentage points).")
                    .append(" Negative-review share: ").append(format(currentNegativeShare)).append("% vs ")
                    .append(format(previousNegativeShare)).append("% (")
                    .append(signed(currentNegativeShare - previousNegativeShare)).append(" percentage points).");
        }
        return result.toString().trim();
    }

    @Transactional(readOnly = true)
    public String summarizePortfolioPerformance(String requestedPeriod) {
        ReviewPeriod period;
        try {
            period = resolvePeriod(requestedPeriod);
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }

        List<HotelPerformance> hotels = repository.findPortfolioPerformance(period);
        if (hotels.isEmpty()) {
            return "No scored reviews were found across the hotel portfolio in the requested period or its comparison window. "
                    + periodContext(period);
        }

        long currentCount = sum(hotels, HotelPerformance::currentReviewCount);
        long currentScoreTotal = sum(hotels, HotelPerformance::currentScoreTotal);
        long currentPositive = sum(hotels, HotelPerformance::currentPositiveCount);
        long currentNeutral = sum(hotels, HotelPerformance::currentNeutralCount);
        long currentNegative = sum(hotels, HotelPerformance::currentNegativeCount);
        long currentReplies = sum(hotels, HotelPerformance::currentReplyCount);
        long previousCount = sum(hotels, HotelPerformance::previousReviewCount);
        long previousScoreTotal = sum(hotels, HotelPerformance::previousScoreTotal);
        long previousPositive = sum(hotels, HotelPerformance::previousPositiveCount);
        long previousNeutral = sum(hotels, HotelPerformance::previousNeutralCount);
        long previousNegative = sum(hotels, HotelPerformance::previousNegativeCount);
        long previousReplies = sum(hotels, HotelPerformance::previousReplyCount);
        Double currentAverage = average(currentScoreTotal, currentCount);
        Double previousAverage = average(previousScoreTotal, previousCount);
        long currentReportingHotels = hotels.stream().filter(hotel -> hotel.currentReviewCount() > 0).count();
        long previousReportingHotels = hotels.stream().filter(hotel -> hotel.previousReviewCount() > 0).count();

        StringBuilder result = new StringBuilder("Portfolio performance. ").append(periodContext(period))
                .append("\nCurrent (").append(currentReportingHotels).append(" hotels with reviews): ")
                .append(metrics(currentCount, currentAverage, currentPositive, currentNeutral, currentNegative, currentReplies))
                .append("\nComparison (").append(previousReportingHotels).append(" hotels with reviews): ")
                .append(metrics(previousCount, previousAverage, previousPositive, previousNeutral,
                        previousNegative, previousReplies));

        appendPortfolioChanges(result, currentCount, currentAverage, currentPositive, currentReplies,
                previousCount, previousAverage, previousPositive, previousReplies);

        List<HotelPerformance> highlights = hotels.stream()
                .filter(hotel -> hotel.currentReviewCount() > 0)
                .sorted(java.util.Comparator.comparingLong(HotelPerformance::currentReviewCount).reversed()
                        .thenComparing(HotelPerformance::hotelName, String.CASE_INSENSITIVE_ORDER))
                .limit(5)
                .toList();
        if (!highlights.isEmpty()) {
            result.append("\nProperty snapshots by current review volume (up to 5; descriptive, not a quality ranking):");
            for (HotelPerformance hotel : highlights) {
                result.append("\n- ").append(hotel.hotelName()).append(": ")
                        .append(hotel.currentReviewCount()).append(" reviews, ")
                        .append(hotel.currentAverageScore() == null
                                ? "average unavailable" : format(hotel.currentAverageScore()) + "/10 average")
                        .append("; comparison ").append(hotel.previousReviewCount()).append(" reviews");
                if (hotel.currentAverageScore() != null && hotel.previousAverageScore() != null) {
                    result.append("; average change ")
                            .append(signed(hotel.currentAverageScore() - hotel.previousAverageScore())).append(" points");
                }
            }
            if (currentReportingHotels > highlights.size()) {
                result.append("\n").append(currentReportingHotels - highlights.size())
                        .append(" additional hotels reported reviews; see the dashboard for the full property breakdown.");
            }
        }
        return result.toString().trim();
    }

    private static void appendPortfolioChanges(
            StringBuilder result,
            long currentCount,
            Double currentAverage,
            long currentPositive,
            long currentReplies,
            long previousCount,
            Double previousAverage,
            long previousPositive,
            long previousReplies
    ) {
        result.append("\nChange vs comparison:");
        if (currentAverage != null && previousAverage != null) {
            result.append(" average ").append(signed(currentAverage - previousAverage)).append(" points;");
        }
        if (previousCount > 0) {
            result.append(" review volume ")
                    .append(signed((double) (currentCount - previousCount) * 100 / previousCount)).append("%;");
        }
        if (currentCount > 0 && previousCount > 0) {
            double positiveShareChange = percent(currentPositive, currentCount)
                    - percent(previousPositive, previousCount);
            double replyRateChange = percent(currentReplies, currentCount)
                    - percent(previousReplies, previousCount);
            result.append(" positive share ").append(signed(positiveShareChange)).append(" percentage points;")
                    .append(" reply rate ").append(signed(replyRateChange)).append(" percentage points;");
        }
        int lastSeparator = result.length() - 1;
        if (result.charAt(lastSeparator) == ';') result.setLength(lastSeparator);
        if (result.charAt(result.length() - 1) == ':') result.append(" not available (insufficient comparison data)");
    }

    private static long sum(List<HotelPerformance> hotels, java.util.function.ToLongFunction<HotelPerformance> metric) {
        return hotels.stream().mapToLong(metric).sum();
    }

    private static Double average(long scoreTotal, long reviewCount) {
        return reviewCount == 0 ? null : (double) scoreTotal / reviewCount;
    }

    @Transactional(readOnly = true)
    public String compareTopicTrends(String requestedHotelId, String requestedPeriod) {
        String hotelId;
        try {
            hotelId = normalizeHotelId(requestedHotelId);
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }

        ReviewPeriod period;
        try {
            period = resolvePeriod(requestedPeriod);
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }

        List<TopicTrend> trends = repository.findTopicTrends(period, hotelId);
        boolean anyData = trends.stream().anyMatch(trend -> trend.currentPositiveCount() + trend.currentNegativeCount()
                + trend.previousPositiveCount() + trend.previousNegativeCount() > 0);
        if (!anyData) {
            return "No topic-matched reviews were found in these periods. " + periodContext(period);
        }

        StringBuilder result = new StringBuilder(periodContext(period)).append('\n')
                .append("Topic counts match text in the corresponding sentiment comment; a review can match multiple topics.\n");
        for (TopicTrend trend : trends) {
            long currentCount = trend.currentPositiveCount() + trend.currentNegativeCount();
            long previousCount = trend.previousPositiveCount() + trend.previousNegativeCount();
            if (currentCount + previousCount == 0) {
                continue;
            }
            result.append("\n").append(trend.topic())
                    .append(": positive mentions ").append(trend.currentPositiveCount())
                    .append(" current (share ").append(percentOrUnavailable(trend.currentPositiveSharePercent()))
                    .append(") vs ").append(trend.previousPositiveCount())
                    .append(" previous; negative mentions ").append(trend.currentNegativeCount())
                    .append(" current (share ").append(percentOrUnavailable(trend.currentNegativeSharePercent()))
                    .append(") vs ").append(trend.previousNegativeCount())
                    .append(" previous (share ").append(percentOrUnavailable(trend.previousNegativeSharePercent())).append(").");
            if (trend.previousNegativeCount() == 0 && trend.currentNegativeCount() > 0) {
                result.append(" Newly observed in the current window.");
            } else if (trend.previousNegativeCount() > 0) {
                result.append(" Negative-count change: ")
                        .append(signed(trend.currentNegativeCount() - trend.previousNegativeCount())).append(".");
            }
            if (trend.currentPositiveSharePercent() != null && trend.previousPositiveSharePercent() != null) {
                result.append(" Positive-share change: ")
                        .append(signed(trend.currentPositiveSharePercent() - trend.previousPositiveSharePercent()))
                        .append(" percentage points.");
            }
            result.append('\n');
        }
        return result.toString().trim();
    }

    @Transactional(readOnly = true)
    public String findReviewEvidence(
            String requestedHotelId,
            String requestedPeriod,
            String requestedSentiment,
            String requestedTopic,
            String requestedReviewerName,
            boolean suggestionsOnly,
            boolean includeReviewerNames
    ) {
        String hotelId;
        String sentiment;
        String reviewerName;
        ReviewTopicCatalog.Topic topic;
        ReviewPeriod period;
        try {
            hotelId = normalizeHotelId(requestedHotelId);
            sentiment = normalizeSentiment(requestedSentiment);
            reviewerName = normalizeReviewerName(requestedReviewerName);
            topic = ReviewTopicCatalog.resolve(requestedTopic);
            period = resolvePeriod(requestedPeriod);
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }

        String topicPattern = topic == null ? null : topic.regex();
        boolean showReviewerNames = includeReviewerNames || reviewerName != null;
        List<ReviewExcerpt> excerpts = repository.findReviewExcerpts(
                period, hotelId, sentiment, topicPattern, reviewerName, suggestionsOnly, showReviewerNames);
        StringBuilder result = new StringBuilder(periodContext(period)).append('\n');

        if (suggestionsOnly) {
            long matchingCount = repository.countSuggestionReviews(
                    period, hotelId, sentiment, topicPattern, reviewerName);
            result.append("Reviews matching suggestion-language patterns: ").append(matchingCount)
                    .append(". Showing at most 8 excerpts.\n");
        } else {
            result.append(showReviewerNames
                    ? "Showing at most 8 review excerpts; available names exclude reviews marked anonymous. These are examples, not a complete review list.\n"
                    : "Showing at most 8 anonymized review excerpts; these are examples, not a complete review list.\n");
        }

        if (excerpts.isEmpty()) {
            return result.append("No matching review excerpts were found.").toString();
        }
        for (ReviewExcerpt excerpt : excerpts) {
            result.append("- [review id ").append(excerpt.reviewId()).append("; ")
                    .append(excerpt.hotelName()).append(", ").append(excerpt.reviewDate())
                    .append(", score ").append(excerpt.score()).append("/10]");
            if (showReviewerNames) {
                String displayReviewerName = excerpt.reviewerName() == null || excerpt.reviewerName().isBlank()
                        ? "not available (anonymous or not provided)"
                        : sanitizeExcerpt(excerpt.reviewerName());
                result.append(" Reviewer: ").append(displayReviewerName).append('.');
            }
            result.append(" — “").append(sanitizeExcerpt(excerpt.text())).append("”\n");
        }
        return result.toString().trim();
    }

    private ReviewPeriod resolvePeriod(String requestedPeriod) {
        return periodResolver.resolve(requestedPeriod, LocalDate.now(clock));
    }

    private static String normalizeHotelId(String requestedHotelId) {
        if (requestedHotelId == null || requestedHotelId.isBlank()) {
            throw new IllegalArgumentException("Choose one hotel using find_hotels before requesting review analysis.");
        }
        String hotelId = requestedHotelId.trim();
        if (!hotelId.matches(HOTEL_ID_PATTERN) || "all".equalsIgnoreCase(hotelId)) {
            throw new IllegalArgumentException("Provide one valid hotel id returned by find_hotels.");
        }
        return hotelId;
    }

    private static String normalizeSentiment(String requestedSentiment) {
        if (requestedSentiment == null || requestedSentiment.isBlank()) {
            return "all";
        }
        return switch (requestedSentiment.trim().toLowerCase(Locale.ROOT)) {
            case "all", "any" -> "all";
            case "positive", "praise", "liked" -> "positive";
            case "negative", "complaint", "complaints", "issue", "issues" -> "negative";
            case "neutral" -> "neutral";
            default -> throw new IllegalArgumentException("Sentiment must be all, positive, neutral, or negative.");
        };
    }

    private static String normalizeReviewerName(String requestedReviewerName) {
        if (requestedReviewerName == null || requestedReviewerName.isBlank()) return null;
        String reviewerName = requestedReviewerName.trim();
        if (reviewerName.length() > MAX_REVIEWER_NAME_LENGTH) {
            throw new IllegalArgumentException("Reviewer-name searches must be at most 100 characters.");
        }
        return reviewerName;
    }

    private static String periodContext(ReviewPeriod period) {
        return "Resolved against current UTC date " + period.asOfDate() + ". Current window: " + period.currentLabel() + " ("
                + period.currentStart() + " to " + period.currentEnd() + "); comparison window: "
                + period.previousLabel() + " (" + period.previousStart() + " to " + period.previousEnd() + ").";
    }

    private static String metrics(long count, Double average, long positive, long neutral, long negative, long replies) {
        String averageText = average == null ? "not available" : format(average) + "/10 average";
        String positiveShare = count == 0 ? "n/a" : format(percent(positive, count)) + "%";
        String negativeShare = count == 0 ? "n/a" : format(percent(negative, count)) + "%";
        return count + " scored reviews, " + averageText + "; " + positive + " positive (" + positiveShare + ")"
                + ", " + neutral + " neutral, " + negative + " negative (" + negativeShare + ")"
                + "; " + replies + " owner replies (" + (count == 0 ? "n/a" : format(percent(replies, count)) + "%") + ")";
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0 : (double) part * 100 / total;
    }

    private static String percentOrUnavailable(Double value) {
        return value == null ? "not available" : format(value) + "%";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String signed(double value) {
        return (value > 0 ? "+" : "") + format(value);
    }

    private static String sanitizeExcerpt(String text) {
        String withoutLinks = URL_PATTERN.matcher(text).replaceAll("[link removed]");
        String withoutEmails = EMAIL_PATTERN.matcher(withoutLinks).replaceAll("[email removed]");
        return PHONE_PATTERN.matcher(withoutEmails).replaceAll("[phone removed]");
    }
}
