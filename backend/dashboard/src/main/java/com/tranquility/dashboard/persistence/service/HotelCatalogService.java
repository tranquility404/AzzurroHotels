package com.tranquility.dashboard.persistence.service;

import com.tranquility.dashboard.dto.HotelReviewSearchPageResponse;
import com.tranquility.dashboard.dto.HotelSummaryResponse;
import com.tranquility.dashboard.dto.ReviewCursorPageResponse;
import com.tranquility.dashboard.persistence.entity.Hotel;
import com.tranquility.dashboard.persistence.entity.Review;
import com.tranquility.dashboard.mapper.HotelSnapshotMapper;
import com.tranquility.dashboard.model.ReviewCursor;
import com.tranquility.dashboard.exception.HotelReviewSearchException;
import com.tranquility.dashboard.persistence.projection.HotelReviewCountProjection;
import com.tranquility.dashboard.persistence.repository.HotelRepository;
import com.tranquility.dashboard.persistence.repository.ReviewRepository;
import com.tranquility.dashboard.service.ReviewCursorCodec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class HotelCatalogService {

    private final HotelRepository hotelRepository;
    private final ReviewRepository reviewRepository;
    private final HotelSnapshotMapper snapshotMapper;
    private final ReviewCursorCodec reviewCursorCodec;

    private static final int DEFAULT_REVIEW_PAGE_SIZE = 20;
    private static final int MAX_REVIEW_PAGE_SIZE = 20;
    private static final int MAX_REVIEW_PAGE_NUMBER = 1_000;

    public HotelCatalogService(
            HotelRepository hotelRepository,
            ReviewRepository reviewRepository,
            HotelSnapshotMapper snapshotMapper,
            ReviewCursorCodec reviewCursorCodec
    ) {
        this.hotelRepository = hotelRepository;
        this.reviewRepository = reviewRepository;
        this.snapshotMapper = snapshotMapper;
        this.reviewCursorCodec = reviewCursorCodec;
    }

    @Transactional(readOnly = true)
    public List<HotelSummaryResponse> listHotels() {
        List<Hotel> hotels = hotelRepository.findAllByOrderByCollectedAtDescHotelIdAsc();
        if (hotels.isEmpty()) {
            return List.of();
        }

        List<String> hotelIds = hotels.stream().map(Hotel::getHotelId).toList();
        Map<String, Long> reviewCounts = reviewRepository.findReviewCountsByHotelIds(hotelIds).stream()
                .collect(Collectors.toMap(
                        HotelReviewCountProjection::getHotelId,
                        HotelReviewCountProjection::getReviewCount));
        return hotels.stream()
                .map(hotel -> toSummary(hotel, reviewCounts.getOrDefault(hotel.getHotelId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<JsonNode> findHotel(String hotelId) {
        return hotelRepository.findById(hotelId)
                .map(hotel -> snapshotMapper.toJson(hotel, reviewRepository.countForHotel(hotelId)));
    }

    @Transactional(readOnly = true)
    public Optional<ReviewCursorPageResponse> findReviews(String hotelId, String cursor, Integer requestedSize) {
        ReviewCursor after = cursor == null ? null : reviewCursorCodec.decode(cursor);
        if (!hotelRepository.existsById(hotelId)) {
            return Optional.empty();
        }

        int pageSize = requestedSize == null
                ? DEFAULT_REVIEW_PAGE_SIZE
                : Math.max(1, Math.min(MAX_REVIEW_PAGE_SIZE, requestedSize));
        PageRequest pageable = PageRequest.of(0, pageSize + 1);
        List<Review> fetched = after == null
                ? reviewRepository.findFirstPageForHotel(hotelId, pageable)
                : reviewRepository.findNextPageForHotel(
                        hotelId, after.sourcePageNumber(), after.position(), pageable);
        boolean hasNext = fetched.size() > pageSize;
        List<Review> page = fetched.subList(0, Math.min(pageSize, fetched.size()));
        String nextCursor = hasNext
                ? reviewCursorCodec.encode(new ReviewCursor(
                        page.getLast().getSourcePageNumber(), page.getLast().getPosition()))
                : null;
        List<JsonNode> data = page.stream().map(review -> review.getSourceData().deepCopy()).toList();
        return Optional.of(new ReviewCursorPageResponse(data, nextCursor, hasNext));
    }

    @Transactional(readOnly = true)
    public Optional<HotelReviewSearchPageResponse> searchReviews(
            String hotelId,
            String requestedSearch,
            String requestedStartDate,
            String requestedEndDate,
            String requestedSentiment,
            String requestedSort,
            int requestedPage,
            int requestedSize
    ) {
        String searchText = normalizeSearch(requestedSearch);
        LocalDate startDate = parseOptionalDate(requestedStartDate, "startDate");
        LocalDate endDate = parseOptionalDate(requestedEndDate, "endDate");
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new HotelReviewSearchException("The start date must be on or before the end date.");
        }
        String sentiment = normalizeSentiment(requestedSentiment);
        String sort = normalizeSort(requestedSort);
        int pageNumber = Math.max(0, Math.min(MAX_REVIEW_PAGE_NUMBER, requestedPage));
        int pageSize = Math.max(1, Math.min(MAX_REVIEW_PAGE_SIZE,
                requestedSize <= 0 ? DEFAULT_REVIEW_PAGE_SIZE : requestedSize));

        if (!hotelRepository.existsById(hotelId)) {
            return Optional.empty();
        }

        Page<Review> page = reviewRepository.searchHotelReviews(
                hotelId,
                startDate == null ? "" : startDate.toString(),
                endDate == null ? "" : endDate.toString(),
                sentiment,
                searchText,
                sort,
                PageRequest.of(pageNumber, pageSize));
        return Optional.of(HotelReviewSearchPageResponse.from(page));
    }

    private static LocalDate parseOptionalDate(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new HotelReviewSearchException(
                    "The " + parameterName + " parameter must use yyyy-MM-dd format.", exception);
        }
    }

    private static String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String search = value.trim();
        if (search.length() > 120) {
            throw new HotelReviewSearchException("Review search text cannot exceed 120 characters.");
        }
        return search;
    }

    private static String normalizeSentiment(String value) {
        String sentiment = value == null || value.isBlank() ? "all" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("all", "positive", "neutral", "negative").contains(sentiment)) {
            throw new HotelReviewSearchException("The selected review sentiment is not supported.");
        }
        return sentiment;
    }

    private static String normalizeSort(String value) {
        String sort = value == null || value.isBlank() ? "date_desc" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("date_desc", "date_asc", "score_desc", "score_asc", "reviewer_asc").contains(sort)) {
            throw new HotelReviewSearchException(
                    "Review sort must be date_desc, date_asc, score_desc, score_asc, or reviewer_asc.");
        }
        return sort;
    }

    private HotelSummaryResponse toSummary(Hotel hotel, long reviewCount) {
        var details = hotel.getHotelDetails();
        var rating = details == null ? null : details.rating();
        return new HotelSummaryResponse(
                hotel.getHotelId(),
                hotel.getHotelName(),
                rating == null || rating.score() == null || rating.score().isNull()
                        ? null
                        : rating.score().asDouble(),
                reviewCount,
                hotel.getCollectedAtText(),
                hotel.getSnapshotObjectId());
    }
}
