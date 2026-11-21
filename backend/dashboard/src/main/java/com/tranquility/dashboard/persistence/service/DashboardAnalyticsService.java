package com.tranquility.dashboard.persistence.service;

import com.tranquility.dashboard.dto.DashboardOverviewResponse;
import com.tranquility.dashboard.dto.DashboardOverviewResponse.Deltas;
import com.tranquility.dashboard.dto.DashboardOverviewResponse.Period;
import com.tranquility.dashboard.dto.DashboardOverviewResponse.Property;
import com.tranquility.dashboard.dto.DashboardOverviewResponse.PropertyPerformance;
import com.tranquility.dashboard.dto.DashboardOverviewResponse.Stats;
import com.tranquility.dashboard.dto.DashboardOverviewResponse.OperationalTopic;
import com.tranquility.dashboard.dto.DashboardOverviewResponse.Trend;
import com.tranquility.dashboard.dto.DashboardOverviewResponse.TrendDay;
import com.tranquility.dashboard.dto.DashboardOverviewResponse.TrendPoint;
import com.tranquility.dashboard.dto.DashboardReviewPageResponse;
import com.tranquility.dashboard.dto.DashboardReviewPageResponse.Review;
import com.tranquility.dashboard.exception.InvalidDashboardFilterException;
import com.tranquility.dashboard.persistence.projection.DashboardDailyReviewStatsProjection;
import com.tranquility.dashboard.persistence.projection.DashboardPropertyProjection;
import com.tranquility.dashboard.persistence.projection.DashboardReviewProjection;
import com.tranquility.dashboard.persistence.projection.DashboardTrendScoreProjection;
import com.tranquility.dashboard.persistence.projection.OperationalTopicProjection;
import com.tranquility.dashboard.persistence.repository.HotelRepository;
import com.tranquility.dashboard.persistence.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Service
public class DashboardAnalyticsService {

    private static final int MAX_PERIOD_DAYS = 366;
    private static final int DEFAULT_REVIEW_PAGE_SIZE = 20;
    private static final int MAX_REVIEW_PAGE_SIZE = 20;
    private static final int MAX_REVIEW_PAGE_NUMBER = 1_000;
    private static final DateTimeFormatter REVIEW_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TREND_LABEL_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final ReviewRepository reviewRepository;
    private final HotelRepository hotelRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public DashboardAnalyticsService(ReviewRepository reviewRepository, HotelRepository hotelRepository) {
        this.reviewRepository = reviewRepository;
        this.hotelRepository = hotelRepository;
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview(
            String requestedPreset,
            String customStartDate,
            String customEndDate,
            String requestedHotelId
    ) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate asOfDate = findAsOfDate(today);
        String preset = normalizePreset(requestedPreset);
        Period period = resolvePeriod(preset, customStartDate, customEndDate, asOfDate);
        String hotelId = normalizeHotelId(requestedHotelId);

        List<Property> properties = hotelRepository.findDashboardProperties().stream()
                .map(DashboardAnalyticsService::toProperty)
                .toList();
        if (hotelId != null && properties.stream().noneMatch(property -> property.id().equals(hotelId))) {
            throw new InvalidDashboardFilterException("The selected hotel does not exist.");
        }

        List<DashboardDailyReviewStatsProjection> dailyStats = reviewRepository.findDashboardDailyStats(
                period.previousStartDate().toString(), period.endDate().toString(), hotelId);
        Map<String, MutableStats> currentByHotel = new HashMap<>();
        Map<String, MutableStats> previousByHotel = new HashMap<>();
        Map<LocalDate, MutableStats> currentByDate = new TreeMap<>();
        MutableStats currentTotal = new MutableStats();
        MutableStats previousTotal = new MutableStats();

        for (DashboardDailyReviewStatsProjection row : dailyStats) {
            LocalDate date = parseReviewDate(row.getReviewDate());
            if (date == null) {
                continue;
            }
            MutableStats rowStats = MutableStats.from(row);
            if (!date.isBefore(period.startDate()) && !date.isAfter(period.endDate())) {
                currentTotal.add(rowStats);
                currentByHotel.computeIfAbsent(row.getHotelId(), ignored -> new MutableStats()).add(rowStats);
                currentByDate.computeIfAbsent(date, ignored -> new MutableStats()).add(rowStats);
            } else if (!date.isBefore(period.previousStartDate()) && !date.isAfter(period.previousEndDate())) {
                previousTotal.add(rowStats);
                previousByHotel.computeIfAbsent(row.getHotelId(), ignored -> new MutableStats()).add(rowStats);
            }
        }

        Map<LocalDate, long[]> scoreCountsByDate = new TreeMap<>();
        List<DashboardTrendScoreProjection> trendScoreRows = reviewRepository.findDashboardTrendScoreCounts(
                period.startDate().toString(), period.endDate().toString(), hotelId);
        for (DashboardTrendScoreProjection row : trendScoreRows) {
            LocalDate date = parseReviewDate(row.getReviewDate());
            Integer score = row.getScore();
            if (date == null || score == null || score < 1 || score > 10) {
                continue;
            }
            scoreCountsByDate.computeIfAbsent(date, ignored -> new long[10])[score - 1]
                    += valueOrZero(row.getReviewCount());
        }

        List<PropertyPerformance> propertyPerformance = properties.stream()
                .filter(property -> hotelId == null || property.id().equals(hotelId))
                .map(property -> toPerformance(
                        property,
                        currentByHotel.get(property.id()),
                        previousByHotel.get(property.id())))
                .sorted(Comparator
                        .comparing(PropertyPerformance::average, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(performance -> performance.property().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<OperationalTopicProjection> topicRows = reviewRepository.findDashboardOperationalTopicCounts(
                period.startDate().toString(), period.endDate().toString(), hotelId);
        List<OperationalTopic> operationalTopics = topicRows.stream()
                .map(DashboardAnalyticsService::toOperationalTopic)
                .toList();

        Stats current = currentTotal.toStats();
        Stats previous = previousTotal.toStats();
        Deltas deltas = toDeltas(current, previous);
        Trend trend = buildTrend(period, currentByDate, scoreCountsByDate);
        int reportingProperties = (int) currentByHotel.values().stream()
                .filter(stats -> stats.count > 0)
                .count();

        return new DashboardOverviewResponse(
                asOfDate,
                period,
                properties,
                current,
                previous,
                deltas,
                reportingProperties,
                propertyPerformance,
                trend,
                operationalTopics);
    }

    @Transactional(readOnly = true)
    public DashboardReviewPageResponse getReviewFeed(
            String startDateValue,
            String endDateValue,
            String requestedHotelId,
            String requestedSentiment,
            String requestedSearch,
            String requestedSort,
            int requestedPage,
            int requestedSize
    ) {
        LocalDate startDate = parseRequiredDate(startDateValue, "startDate");
        LocalDate endDate = parseRequiredDate(endDateValue, "endDate");
        validateDateRange(startDate, endDate, LocalDate.now(ZoneOffset.UTC));
        String hotelId = normalizeHotelId(requestedHotelId);
        String sentiment = normalizeSentiment(requestedSentiment);
        String searchText = normalizeSearch(requestedSearch);
        String sort = normalizeReviewSort(requestedSort);
        int pageNumber = Math.max(0, Math.min(MAX_REVIEW_PAGE_NUMBER, requestedPage));
        int pageSize = Math.max(1, Math.min(MAX_REVIEW_PAGE_SIZE,
                requestedSize <= 0 ? DEFAULT_REVIEW_PAGE_SIZE : requestedSize));

        Page<DashboardReviewProjection> page = reviewRepository.findDashboardReviewFeed(
                startDate.toString(), endDate.toString(), hotelId, sentiment, searchText, sort,
                PageRequest.of(pageNumber, pageSize));
        List<Review> reviews = page.getContent().stream()
                .map(this::toReview)
                .filter(Objects::nonNull)
                .toList();

        return new DashboardReviewPageResponse(
                reviews,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }

    private LocalDate findAsOfDate(LocalDate today) {
        String latestDate = reviewRepository.findLatestReviewDate(today.toString());
        LocalDate parsed = parseReviewDate(latestDate);
        return parsed == null ? today : parsed;
    }

    private Period resolvePeriod(String preset, String customStartDate, String customEndDate, LocalDate asOfDate) {
        LocalDate startDate;
        LocalDate endDate;
        String comparisonLabel;
        switch (preset) {
            case "week" -> {
                endDate = asOfDate;
                startDate = asOfDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                comparisonLabel = "vs previous week";
            }
            case "7d" -> {
                endDate = asOfDate;
                startDate = endDate.minusDays(6);
                comparisonLabel = "vs prior 7 days";
            }
            case "30d" -> {
                endDate = asOfDate;
                startDate = endDate.minusDays(29);
                comparisonLabel = "vs prior 30 days";
            }
            case "90d" -> {
                endDate = asOfDate;
                startDate = endDate.minusDays(89);
                comparisonLabel = "vs prior 90 days";
            }
            case "custom" -> {
                startDate = parseRequiredDate(customStartDate, "startDate");
                endDate = parseRequiredDate(customEndDate, "endDate");
                comparisonLabel = "vs prior period";
            }
            default -> throw new InvalidDashboardFilterException("The selected dashboard period is not supported.");
        }

        validateDateRange(startDate, endDate, asOfDate);
        int dayCount = Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);
        LocalDate previousEndDate = startDate.minusDays(1);
        LocalDate previousStartDate = previousEndDate.minusDays(dayCount - 1L);
        return new Period(preset, startDate, endDate, previousStartDate, previousEndDate, dayCount, comparisonLabel);
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate, LocalDate asOfDate) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidDashboardFilterException("The start date must be on or before the end date.");
        }
        if (endDate.isAfter(asOfDate)) {
            throw new InvalidDashboardFilterException("The end date cannot be later than the latest available date.");
        }
        long dayCount = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (dayCount > MAX_PERIOD_DAYS) {
            throw new InvalidDashboardFilterException("The selected dashboard period cannot exceed 366 days.");
        }
    }

    private static LocalDate parseRequiredDate(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new InvalidDashboardFilterException("The " + parameterName + " parameter is required.");
        }
        try {
            return LocalDate.parse(value, REVIEW_DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new InvalidDashboardFilterException(
                    "The " + parameterName + " parameter must use yyyy-MM-dd format.", exception);
        }
    }

    private static String normalizePreset(String value) {
        String preset = value == null ? "week" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("week", "7d", "30d", "90d", "custom").contains(preset)) {
            throw new InvalidDashboardFilterException("The selected dashboard period is not supported.");
        }
        return preset;
    }

    private static String normalizeHotelId(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String hotelId = value.trim();
        if (!hotelId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new InvalidDashboardFilterException("The selected hotel id is invalid.");
        }
        return hotelId;
    }

    private static String normalizeSentiment(String value) {
        String sentiment = value == null || value.isBlank() ? "all" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("all", "positive", "neutral", "negative").contains(sentiment)) {
            throw new InvalidDashboardFilterException("The selected sentiment is not supported.");
        }
        return sentiment;
    }

    private static String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String search = value.trim();
        if (search.length() > 120) {
            throw new InvalidDashboardFilterException("Review search text cannot exceed 120 characters.");
        }
        return search;
    }

    private static String normalizeReviewSort(String value) {
        String sort = value == null || value.isBlank() ? "date_desc" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("date_desc", "date_asc", "score_desc", "score_asc", "reviewer_asc").contains(sort)) {
            throw new InvalidDashboardFilterException(
                    "Review sort must be date_desc, date_asc, score_desc, score_asc, or reviewer_asc.");
        }
        return sort;
    }

    private static LocalDate parseReviewDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, REVIEW_DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static Property toProperty(DashboardPropertyProjection projection) {
        return new Property(
                projection.getHotelId(),
                projection.getHotelName(),
                projection.getCity(),
                projection.getCountry());
    }

    private static PropertyPerformance toPerformance(Property property, MutableStats current, MutableStats previous) {
        Stats currentStats = (current == null ? new MutableStats() : current).toStats();
        Stats previousStats = (previous == null ? new MutableStats() : previous).toStats();
        Double delta = currentStats.average() == null || previousStats.average() == null
                ? null
                : currentStats.average() - previousStats.average();
        return new PropertyPerformance(
                property,
                currentStats.count(),
                currentStats.average(),
                delta,
                previousStats.count());
    }

    private static Deltas toDeltas(Stats current, Stats previous) {
        Double averageRating = current.average() == null || previous.average() == null
                ? null
                : current.average() - previous.average();
        Double reviewCountPercent = previous.count() == 0
                ? null
                : ((double) (current.count() - previous.count()) / previous.count()) * 100;
        Double positiveSharePoints = current.count() == 0 || previous.count() == 0
                ? null
                : current.positiveShare() - previous.positiveShare();
        Double replyRatePoints = current.count() == 0 || previous.count() == 0
                ? null
                : current.replyRate() - previous.replyRate();
        return new Deltas(averageRating, reviewCountPercent, positiveSharePoints, replyRatePoints);
    }

    private static Trend buildTrend(
            Period period,
            Map<LocalDate, MutableStats> statsByDate,
            Map<LocalDate, long[]> scoreCountsByDate
    ) {
        Map<LocalDate, MutableStats> statsByWeek = new TreeMap<>();
        statsByDate.forEach((date, stats) -> {
            LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            statsByWeek.computeIfAbsent(monday, ignored -> new MutableStats()).add(stats);
        });

        List<TrendPoint> points = new ArrayList<>();
        LocalDate week = period.startDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastWeek = period.endDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!week.isAfter(lastWeek)) {
            MutableStats stats = statsByWeek.getOrDefault(week, new MutableStats());
            points.add(new TrendPoint(
                    week,
                    week.plusDays(6),
                    week.format(TREND_LABEL_FORMAT),
                    stats.positiveCount,
                    stats.neutralCount,
                    stats.negativeCount,
                    stats.count));
            week = week.plusWeeks(1);
        }

        long positiveTotal = points.stream().mapToLong(TrendPoint::positive).sum();
        long negativeTotal = points.stream().mapToLong(TrendPoint::negative).sum();
        long classifiedTotal = positiveTotal + negativeTotal;
        Double positiveVsNegativePercent = classifiedTotal == 0
                ? null
                : (double) positiveTotal / classifiedTotal * 100;
        double averagePositivePerWeek = points.isEmpty() ? 0 : (double) positiveTotal / points.size();
        String bestWeekLabel = points.stream()
                .max(Comparator.comparingLong(TrendPoint::positive))
                .filter(point -> point.positive() > 0)
                .map(TrendPoint::label)
                .orElse(null);
        String accessibleSummary = points.stream()
                .map(point -> point.label() + ": " + point.positive() + " positive, " + point.negative() + " negative")
                .reduce((left, right) -> left + "; " + right)
                .orElse("No review trend data for this period.");

        return new Trend(
                points,
                positiveTotal,
                negativeTotal,
                positiveVsNegativePercent,
                averagePositivePerWeek,
                bestWeekLabel,
                accessibleSummary,
                buildDailyTrendPoints(period, scoreCountsByDate));
    }

    private static List<TrendDay> buildDailyTrendPoints(Period period, Map<LocalDate, long[]> scoreCountsByDate) {
        List<TrendDay> dailyPoints = new ArrayList<>(period.dayCount());
        for (LocalDate date = period.startDate(); !date.isAfter(period.endDate()); date = date.plusDays(1)) {
            long[] counts = scoreCountsByDate.getOrDefault(date, new long[10]);
            List<Long> scoreCounts = new ArrayList<>(counts.length);
            long total = 0;
            for (long count : counts) {
                scoreCounts.add(count);
                total += count;
            }
            dailyPoints.add(new TrendDay(date, List.copyOf(scoreCounts), total));
        }
        return List.copyOf(dailyPoints);
    }

    private static OperationalTopic toOperationalTopic(OperationalTopicProjection row) {
        return new OperationalTopic(
                row.getTopic(),
                valueOrZero(row.getReviewCount()),
                valueOrZero(row.getPositiveReviewCount()),
                valueOrZero(row.getNeutralReviewCount()),
                valueOrZero(row.getNegativeReviewCount()),
                row.getPositiveReviewSharePercent(),
                row.getNegativeReviewSharePercent());
    }

    private Review toReview(DashboardReviewProjection projection) {
        JsonNode source = readSourceData(projection.getSourceDataJson());
        JsonNode guest = source.path("guest");
        JsonNode stay = source.path("stay");
        String positive = readString(source.path("positive"));
        String negative = readString(source.path("negative"));
        boolean anonymous = guest.path("is_anonymous").asBoolean(false);
        String guestName = anonymous ? "Anonymous guest" : defaultString(readString(guest.path("name")), "Guest");
        String guestCountry = defaultString(
                readString(guest.path("country")),
                defaultString(readString(guest.path("country_code")), "Country unavailable"));
        String city = projection.getCity();
        String country = projection.getCountry();
        String propertyCity = country != null && !"Location unavailable".equals(country)
                && city != null && !city.equalsIgnoreCase(country)
                ? city + ", " + country
                : defaultString(city, "Location unavailable");
        int score = projection.getScore() == null ? 0 : projection.getScore();

        return new Review(
                projection.getId().toString(),
                projection.getHotelId(),
                projection.getHotelName(),
                propertyCity,
                score,
                sentimentOf(score),
                defaultString(readString(source.path("title")), defaultString(positive, defaultString(negative, "Guest review"))),
                projection.getReviewDate(),
                positive,
                negative,
                guestName,
                guestCountry,
                defaultString(readString(guest.path("type")), "Guest"),
                defaultString(readString(stay.path("room_name")), "Room not provided"),
                readInteger(stay.path("nights")),
                defaultString(readString(source.path("language")), "und"),
                readString(firstPresent(source, "owner_reply", "ownerReply")),
                readTopics(firstPresent(source, "positive_topics", "positiveTopics")),
                readTopics(firstPresent(source, "negative_topics", "negativeTopics")));
    }

    private JsonNode readSourceData(String json) {
        if (json == null || json.isBlank()) {
            return jsonMapper.createObjectNode();
        }
        try {
            return jsonMapper.readTree(json);
        } catch (Exception exception) {
            return jsonMapper.createObjectNode();
        }
    }

    private static String readString(JsonNode value) {
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String result = value.asString().trim();
        return result.isEmpty() ? null : result;
    }

    private static JsonNode firstPresent(JsonNode source, String firstName, String secondName) {
        JsonNode first = source.path(firstName);
        return first.isMissingNode() || first.isNull() ? source.path(secondName) : first;
    }

    private static Integer readInteger(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asInt();
        }
        String text = readString(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static List<String> readTopics(JsonNode values) {
        if (values == null || !values.isArray()) {
            return List.of();
        }
        List<String> topics = new ArrayList<>();
        for (JsonNode value : values) {
            String topic = value.isObject() ? readString(value.path("name")) : readString(value);
            if (topic != null) {
                topics.add(topic);
            }
        }
        return List.copyOf(topics);
    }

    private static String sentimentOf(int score) {
        if (score >= 8) {
            return "positive";
        }
        return score <= 6 ? "negative" : "neutral";
    }

    private static String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }

    private static final class MutableStats {
        private long count;
        private long scoreTotal;
        private long positiveCount;
        private long neutralCount;
        private long negativeCount;
        private long replyCount;

        private static MutableStats from(DashboardDailyReviewStatsProjection row) {
            MutableStats stats = new MutableStats();
            stats.count = valueOrZero(row.getReviewCount());
            stats.scoreTotal = valueOrZero(row.getScoreTotal());
            stats.positiveCount = valueOrZero(row.getPositiveCount());
            stats.neutralCount = valueOrZero(row.getNeutralCount());
            stats.negativeCount = valueOrZero(row.getNegativeCount());
            stats.replyCount = valueOrZero(row.getReplyCount());
            return stats;
        }

        private void add(MutableStats other) {
            count += other.count;
            scoreTotal += other.scoreTotal;
            positiveCount += other.positiveCount;
            neutralCount += other.neutralCount;
            negativeCount += other.negativeCount;
            replyCount += other.replyCount;
        }

        private Stats toStats() {
            Double average = count == 0 ? null : (double) scoreTotal / count;
            double positiveShare = count == 0 ? 0 : (double) positiveCount / count * 100;
            double replyRate = count == 0 ? 0 : (double) replyCount / count * 100;
            return new Stats(count, average, positiveCount, neutralCount, negativeCount,
                    positiveShare, replyCount, replyRate);
        }
    }
}
