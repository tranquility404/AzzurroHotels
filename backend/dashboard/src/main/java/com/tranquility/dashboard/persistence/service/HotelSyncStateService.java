package com.tranquility.dashboard.persistence.service;

import com.tranquility.collection.model.HotelReviewPage;
import com.tranquility.dashboard.dto.HotelSyncResponse;
import com.tranquility.dashboard.model.HotelSyncReviewIdentity;
import com.tranquility.dashboard.model.HotelSyncReviewPreview;
import com.tranquility.dashboard.persistence.entity.Hotel;
import com.tranquility.dashboard.model.HotelSyncStatus;
import com.tranquility.dashboard.persistence.entity.HotelSyncPendingBatch;
import com.tranquility.dashboard.persistence.entity.Review;
import com.tranquility.dashboard.mapper.HotelReviewSourceDataMapper;
import com.tranquility.dashboard.exception.HotelSyncConflictException;
import com.tranquility.dashboard.exception.HotelSyncNotFoundException;
import com.tranquility.dashboard.exception.HotelSyncRateLimitException;
import com.tranquility.dashboard.persistence.repository.HotelRepository;
import com.tranquility.dashboard.persistence.repository.HotelSyncPendingBatchRepository;
import com.tranquility.dashboard.persistence.repository.ReviewRepository;
import com.tranquility.shared.storage.model.UploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HotelSyncStateService {

    private static final int MAX_PROVIDER_REVIEW_PAGES = 400;
    private static final int PROVIDER_REVIEW_PAGE_SIZE = 25;
    private static final Duration DAILY_SYNC_INTERVAL = Duration.ofHours(24);

    private final HotelRepository hotelRepository;
    private final ReviewRepository reviewRepository;
    private final HotelReviewSourceDataMapper reviewMapper;
    private final HotelSyncPendingBatchRepository pendingBatchRepository;
    private final HotelSnapshotPersistenceService snapshotPersistenceService;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Transactional
    public InitialClaim claimInitialSync(String hotelId) {
        Hotel hotel = lockHotel(hotelId);
        Instant now = clock.instant();
        Instant lastStartedAt = hotel.getLastSyncStartedAt();
        Instant nextAllowedAt = lastStartedAt == null ? null : lastStartedAt.plus(DAILY_SYNC_INTERVAL);

        if ((hotel.getSyncStatus() == HotelSyncStatus.SYNCING
                || hotel.getSyncStatus() == HotelSyncStatus.AWAITING_CONFIRMATION)
                && nextAllowedAt != null && now.isBefore(nextAllowedAt)) {
            throw new HotelSyncConflictException("A sync for this hotel is already in progress.");
        }
        if (nextAllowedAt != null && now.isBefore(nextAllowedAt)) {
            throw new HotelSyncRateLimitException(nextAllowedAt);
        }

        // A preview left behind beyond the daily window belongs to an expired run.
        // Remove it before starting a new run because the table permits one pending
        // batch per hotel.
        pendingBatchRepository.deleteByHotelId(hotelId);
        UUID token = UUID.randomUUID();
        hotel.beginSync(now, token);
        hotelRepository.saveAndFlush(hotel);
        return new InitialClaim(hotel.getHotelId(), hotel.getRequestedUrl(), token);
    }

    @Transactional
    public ContinuationClaim claimContinuation(String hotelId, UUID token) {
        Hotel hotel = lockHotel(hotelId);
        if (token == null
                || hotel.getSyncStatus() != HotelSyncStatus.AWAITING_CONTINUATION
                || !token.equals(hotel.getSyncContinuationToken())
                || hotel.getSyncNextPage() == null) {
            throw new HotelSyncConflictException("This hotel sync continuation is invalid or no longer active.");
        }

        hotel.claimSyncContinuation(token);
        hotelRepository.saveAndFlush(hotel);
        return new ContinuationClaim(
                hotel.getHotelId(), hotel.getRequestedUrl(), hotel.getHotelName(), token, hotel.getSyncNextPage());
    }

    @Transactional
    public HotelSyncResponse stageInitialPage(
            InitialClaim claim,
            String hotelName,
            JsonNode snapshot,
            HotelReviewPage page
    ) {
        Hotel hotel = lockHotel(claim.hotelId());
        requireActiveSync(hotel, claim.continuationToken());
        if (page.currentPage() != 1) {
            throw new HotelSyncConflictException("The provider did not return the first review page.");
        }

        Integer minimumExistingPage = reviewRepository.findMinimumSourcePageNumberForHotel(claim.hotelId());
        int sourcePageStart = (minimumExistingPage == null ? 1 : minimumExistingPage) - MAX_PROVIDER_REVIEW_PAGES;
        PreviewImport preview = classifyReviews(hotel, page, sourcePageStart, false);
        boolean hasMore = page.hasNext() && preview.alreadyStoredCount() == 0;
        boolean recommendUnrestricted = hasMore
                && page.results().size() == PROVIDER_REVIEW_PAGE_SIZE
                && preview.newCount() == PROVIDER_REVIEW_PAGE_SIZE;

        HotelSyncPendingBatch batch = createPendingBatch(
                hotel.getHotelId(), claim.continuationToken(), page, sourcePageStart, snapshot);
        hotel.awaitSyncConfirmation(claim.continuationToken(), sourcePageStart, page.totalPages());
        hotelRepository.saveAndFlush(hotel);

        String message = hasMore
                ? recommendUnrestricted
                    ? "Review the refreshed hotel details and first review page. All 25 reviews appear new; more pages may be needed to find the existing-review boundary."
                    : "Review the refreshed hotel details and first review page before saving."
                : "Review the refreshed hotel details and latest review candidates before saving.";
        return previewResponse(
                hotel, hotelName, batch, page, preview, true, hasMore, recommendUnrestricted, message);
    }

    @Transactional
    public HotelSyncResponse stageContinuationPage(ContinuationClaim claim, HotelReviewPage page) {
        Hotel hotel = lockHotel(claim.hotelId());
        requireActiveSync(hotel, claim.continuationToken());
        if (hotel.getSyncNextPage() == null || page.currentPage() != hotel.getSyncNextPage()) {
            throw new HotelSyncConflictException("The provider returned an unexpected review page.");
        }

        int sourcePageStart = Objects.requireNonNull(hotel.getSyncSourcePageStart());
        PreviewImport preview = classifyReviews(hotel, page, sourcePageStart, true);
        boolean hasMore = !preview.overlapFound() && page.hasNext();
        HotelSyncPendingBatch batch = createPendingBatch(
                hotel.getHotelId(), claim.continuationToken(), page, sourcePageStart, null);
        hotel.awaitSyncConfirmation(claim.continuationToken());
        hotelRepository.saveAndFlush(hotel);

        String message = preview.overlapFound()
                ? "This page reaches a review already stored. Confirm to save only the new reviews before that boundary."
                : hasMore
                    ? "Review this page before saving it and continuing to the next provider page."
                    : "Review the final page candidates before saving.";
        return previewResponse(
                hotel, claim.hotelName(), batch, page, preview, false, hasMore, false, message);
    }

    @Transactional(readOnly = true)
    public HotelSyncResponse findPendingPreview(String hotelId, UUID continuationToken) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelSyncNotFoundException(hotelId));
        if (continuationToken == null
                || !continuationToken.equals(hotel.getSyncContinuationToken())) {
            throw new HotelSyncConflictException("This hotel sync run is no longer active.");
        }
        if (hotel.getSyncStatus() == HotelSyncStatus.AWAITING_CONTINUATION) {
            return new HotelSyncResponse(
                    hotel.getHotelId(), hotel.getHotelName(), nextAllowedAt(hotel), false,
                    hotel.getSyncNextPage() == null ? 0 : hotel.getSyncNextPage() - 1,
                    hotel.getSyncTotalPages() == null ? 0 : hotel.getSyncTotalPages(),
                    hotel.getSyncNextPage(), hotel.getSyncPagesFetched(), 0, 0, 0,
                    hotel.getSyncReviewsAdded(), hotel.getSyncReviewsSkipped(), true, false,
                    continuationToken, "The previous page was confirmed. You may preview the next review page.",
                    false, null, null, List.of(), 0, 0, 0, 0);
        }
        if (hotel.getSyncStatus() != HotelSyncStatus.AWAITING_CONFIRMATION) {
            throw new HotelSyncConflictException("There is no pending preview for this hotel sync run.");
        }
        HotelSyncPendingBatch batch = pendingBatchRepository.findByHotelId(hotelId)
                .filter(candidate -> continuationToken.equals(candidate.getContinuationToken()))
                .orElseThrow(() -> new HotelSyncConflictException("The pending hotel sync preview is unavailable."));
        HotelReviewPage page = batch.toReviewPage();
        PreviewImport preview = classifyReviews(hotel, page, batch.getSourcePageStart(), page.currentPage() > 1);
        boolean hasMore = page.hasNext() && !preview.overlapFound();
        boolean unrestricted = page.currentPage() == 1
                && hasMore
                && page.results().size() == PROVIDER_REVIEW_PAGE_SIZE
                && preview.newCount() == PROVIDER_REVIEW_PAGE_SIZE;
        return previewResponse(
                hotel,
                snapshotHotelName(batch.snapshotCopy(), hotel.getHotelName()),
                batch,
                page,
                preview,
                batch.snapshotCopy() != null,
                hasMore,
                unrestricted,
                "A hotel sync preview is waiting for your confirmation.");
    }

    @Transactional(readOnly = true)
    public HotelSyncPendingBatch pendingBatchForConfirmation(String hotelId, UUID token, UUID batchId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelSyncNotFoundException(hotelId));
        if (token == null
                || hotel.getSyncStatus() != HotelSyncStatus.AWAITING_CONFIRMATION
                || !token.equals(hotel.getSyncContinuationToken())) {
            throw new HotelSyncConflictException("This hotel sync preview is no longer awaiting confirmation.");
        }
        return pendingBatchRepository.findByHotelId(hotelId)
                .filter(batch -> batch.getBatchId().equals(batchId)
                        && batch.getContinuationToken().equals(token))
                .orElseThrow(() -> new HotelSyncConflictException("This hotel sync preview is no longer available."));
    }

    @Transactional
    public HotelSyncResponse confirmPendingBatch(String hotelId, UUID token, UUID batchId, UploadResult snapshotUpload) {
        Hotel hotel = lockHotel(hotelId);
        HotelSyncPendingBatch batch = pendingBatchRepository.findByHotelAndBatchForUpdate(hotelId, batchId)
                .orElseThrow(() -> new HotelSyncConflictException("This hotel sync preview is no longer available."));
        requirePendingConfirmation(hotel, batch, token);

        JsonNode candidateSnapshot = batch.snapshotCopy();
        if ((candidateSnapshot == null) != (snapshotUpload == null)) {
            throw new HotelSyncConflictException("The hotel details upload does not match the pending preview.");
        }
        HotelReviewPage page = batch.toReviewPage();
        int sourcePageStart = batch.getSourcePageStart();
        boolean stopAtOverlap = page.currentPage() > 1;
        PreviewImport imported = classifyReviews(hotel, page, sourcePageStart, stopAtOverlap);

        hotel.beginSyncConfirmation(token);
        if (candidateSnapshot != null) {
            snapshotPersistenceService.persist(candidateSnapshot, snapshotUpload);
        }
        if (!imported.newReviews().isEmpty()) {
            reviewRepository.saveAll(imported.newReviews());
        }

        int pagesFetched = hotel.getSyncPagesFetched() + 1;
        int totalAdded = hotel.getSyncReviewsAdded() + imported.newReviews().size();
        int totalSkipped = hotel.getSyncReviewsSkipped() + imported.skippedCount();
        boolean hasMore = !imported.overlapFound() && page.hasNext();
        boolean recommendUnrestricted = page.currentPage() == 1
                && hasMore
                && page.results().size() == PROVIDER_REVIEW_PAGE_SIZE
                && imported.newCount() == PROVIDER_REVIEW_PAGE_SIZE;

        if (hasMore) {
            hotel.awaitSyncContinuation(
                    token, sourcePageStart, page.currentPage() + 1, page.totalPages(),
                    pagesFetched, totalAdded, totalSkipped);
        } else {
            hotel.completeSync(token, sourcePageStart, page.totalPages(), pagesFetched, totalAdded, totalSkipped);
        }
        pendingBatchRepository.delete(batch);
        hotelRepository.saveAndFlush(hotel);

        String message = imported.newReviews().isEmpty()
                ? hasMore
                    ? "No new reviews found on this page. Hotel details synced successfully; you may preview the next page."
                    : "No new reviews found. Hotel data synced successfully."
                : hasMore
                    ? recommendUnrestricted
                        ? "This page was saved. All 25 reviews were new; preview the next page to find the existing-review boundary."
                        : "This page was saved. You may preview the next provider page."
                    : imported.overlapFound()
                        ? "This page was saved up to the first review already in the database."
                        : "The confirmed hotel details and reviews were saved.";
        return new HotelSyncResponse(
                hotel.getHotelId(),
                snapshotHotelName(candidateSnapshot, hotel.getHotelName()),
                nextAllowedAt(hotel),
                candidateSnapshot != null,
                page.currentPage(),
                page.totalPages(),
                hasMore ? page.currentPage() + 1 : null,
                hotel.getSyncPagesFetched(),
                page.results().size(),
                imported.newReviews().size(),
                imported.skippedCount(),
                hotel.getSyncReviewsAdded(),
                hotel.getSyncReviewsSkipped(),
                hasMore,
                recommendUnrestricted,
                hasMore ? token : null,
                message,
                false,
                null,
                null,
                List.of(),
                0,
                0,
                0,
                0);
    }

    @Transactional
    public HotelSyncResponse rejectPendingBatch(String hotelId, UUID token, UUID batchId) {
        Hotel hotel = lockHotel(hotelId);
        HotelSyncPendingBatch batch = pendingBatchRepository.findByHotelAndBatchForUpdate(hotelId, batchId)
                .orElseThrow(() -> new HotelSyncConflictException("This hotel sync preview is no longer available."));
        requirePendingConfirmation(hotel, batch, token);

        HotelReviewPage page = batch.toReviewPage();
        PreviewImport preview = classifyReviews(hotel, page, batch.getSourcePageStart(), page.currentPage() > 1);
        String hotelName = snapshotHotelName(batch.snapshotCopy(), hotel.getHotelName());
        pendingBatchRepository.delete(batch);
        hotel.cancelSync(token);
        hotelRepository.saveAndFlush(hotel);

        return new HotelSyncResponse(
                hotel.getHotelId(), hotelName, nextAllowedAt(hotel), false,
                page.currentPage(), page.totalPages(), null, hotel.getSyncPagesFetched(),
                page.results().size(), 0, 0, hotel.getSyncReviewsAdded(), hotel.getSyncReviewsSkipped(),
                false, false, null, "This preview was rejected. No candidate details or reviews were saved; sync continuation was stopped.",
                false, null, null, preview.reviewPreviews(), 0, preview.alreadyStoredCount(),
                preview.duplicateCount(), preview.beyondBoundaryCount());
    }

    @Transactional
    public Instant failInitialSync(String hotelId, UUID token) {
        return hotelRepository.findByHotelIdForUpdate(hotelId)
                .map(hotel -> {
                    hotel.failSync(token);
                    hotelRepository.saveAndFlush(hotel);
                    return nextAllowedAt(hotel);
                })
                .orElse(null);
    }

    @Transactional
    public void releaseContinuation(String hotelId, UUID token) {
        hotelRepository.findByHotelIdForUpdate(hotelId).ifPresent(hotel -> {
            hotel.recordContinuationFailure(token);
            hotelRepository.saveAndFlush(hotel);
        });
    }

    private HotelSyncPendingBatch createPendingBatch(
            String hotelId,
            UUID token,
            HotelReviewPage page,
            int sourcePageStart,
            JsonNode snapshot
    ) {
        if (pendingBatchRepository.findByHotelId(hotelId).isPresent()) {
            throw new HotelSyncConflictException("A preview for this hotel is already awaiting confirmation.");
        }
        ArrayNode results = jsonMapper.createArrayNode();
        page.results().forEach(result -> results.add(result.deepCopy()));
        HotelSyncPendingBatch batch = new HotelSyncPendingBatch(
                UUID.randomUUID(), hotelId, token, page, sourcePageStart, snapshot, results, clock.instant());
        return pendingBatchRepository.saveAndFlush(batch);
    }

    private PreviewImport classifyReviews(Hotel hotel, HotelReviewPage page, int sourcePageStart, boolean stopAtOverlap) {
        Set<String> distinctIds = new HashSet<>();
        Set<String> reviewDates = new HashSet<>();
        Set<Integer> scores = new HashSet<>();
        List<HotelSyncReviewIdentity> contentIdentities = new ArrayList<>(page.results().size());
        for (JsonNode data : page.results()) {
            String id = reviewMapper.sourceReviewId(data);
            if (id != null) {
                distinctIds.add(id);
            }
            HotelSyncReviewIdentity identity = HotelSyncReviewIdentity.from(data).orElse(null);
            contentIdentities.add(identity);
            if (identity != null) {
                reviewDates.add(identity.date());
                scores.add(identity.score());
            }
        }
        Set<String> existingIds = distinctIds.isEmpty()
                ? Set.of()
                : new HashSet<>(reviewRepository.findExistingSourceReviewIds(hotel.getHotelId(), new ArrayList<>(distinctIds)));
        Set<HotelSyncReviewIdentity> existingContentIdentities = reviewDates.isEmpty()
                ? Set.of()
                : reviewRepository.findReviewIdentityCandidates(
                                hotel.getHotelId(), new ArrayList<>(reviewDates), new ArrayList<>(scores)).stream()
                        .map(HotelSyncReviewIdentity::from)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toSet());

        Set<String> seen = new HashSet<>();
        Set<HotelSyncReviewIdentity> seenContentIdentities = new HashSet<>();
        List<Review> newReviews = new ArrayList<>();
        List<HotelSyncReviewPreview> reviewPreviews = new ArrayList<>();
        int newCount = 0;
        int existingCount = 0;
        int duplicateCount = 0;
        int beyondBoundaryCount = 0;
        int invalidCount = 0;
        boolean boundaryReached = false;
        for (int position = 0; position < page.results().size(); position++) {
            JsonNode data = page.results().get(position);
            String id = reviewMapper.sourceReviewId(data);
            HotelSyncReviewIdentity identity = contentIdentities.get(position);
            String classification;
            if (id == null) {
                classification = "invalid";
                invalidCount++;
            } else if (!seen.add(id)) {
                classification = "duplicate";
                duplicateCount++;
            } else if (stopAtOverlap && boundaryReached) {
                classification = "beyond_boundary";
                beyondBoundaryCount++;
            } else if (identity != null && !seenContentIdentities.add(identity)) {
                classification = "duplicate";
                duplicateCount++;
            } else if (existingIds.contains(id) || (identity != null && existingContentIdentities.contains(identity))) {
                classification = "already_stored";
                existingCount++;
                if (stopAtOverlap) boundaryReached = true;
            } else {
                classification = "new";
                newCount++;
                int sourcePageNumber = sourcePageStart + page.currentPage() - 1;
                newReviews.add(reviewMapper.toReview(hotel, sourcePageNumber, position, data));
            }
            reviewPreviews.add(new HotelSyncReviewPreview(position, id, classification, data.deepCopy()));
        }
        int skippedCount = existingCount + duplicateCount + beyondBoundaryCount + invalidCount;
        return new PreviewImport(
                newReviews, reviewPreviews, newCount, existingCount, duplicateCount,
                beyondBoundaryCount, invalidCount, skippedCount);
    }

    private HotelSyncResponse previewResponse(
            Hotel hotel,
            String hotelName,
            HotelSyncPendingBatch batch,
            HotelReviewPage page,
            PreviewImport preview,
            boolean candidateDetails,
            boolean hasMore,
            boolean unrestricted,
            String message
    ) {
        JsonNode snapshot = batch.snapshotCopy();
        return new HotelSyncResponse(
                hotel.getHotelId(),
                hotelName,
                nextAllowedAt(hotel),
                false,
                page.currentPage(),
                page.totalPages(),
                hasMore ? page.currentPage() + 1 : null,
                hotel.getSyncPagesFetched(),
                page.results().size(),
                0,
                0,
                hotel.getSyncReviewsAdded(),
                hotel.getSyncReviewsSkipped(),
                hasMore,
                unrestricted,
                hotel.getSyncContinuationToken(),
                message,
                true,
                batch.getBatchId(),
                candidateDetails ? snapshot : null,
                preview.reviewPreviews(),
                preview.newCount(),
                preview.alreadyStoredCount(),
                preview.duplicateCount(),
                preview.beyondBoundaryCount());
    }

    private static void requirePendingConfirmation(Hotel hotel, HotelSyncPendingBatch batch, UUID token) {
        if (token == null
                || hotel.getSyncStatus() != HotelSyncStatus.AWAITING_CONFIRMATION
                || !token.equals(hotel.getSyncContinuationToken())
                || !token.equals(batch.getContinuationToken())
                || !hotel.getHotelId().equals(batch.getHotelId())) {
            throw new HotelSyncConflictException("This hotel sync preview is no longer awaiting confirmation.");
        }
    }

    private static String snapshotHotelName(JsonNode snapshot, String fallback) {
        JsonNode name = snapshot == null ? null : snapshot.get("hotelName");
        return name != null && name.isString() ? name.asString() : fallback;
    }

    private static Instant nextAllowedAt(Hotel hotel) {
        Instant startedAt = hotel.getLastSyncStartedAt();
        return startedAt == null ? null : startedAt.plus(DAILY_SYNC_INTERVAL);
    }

    private Hotel lockHotel(String hotelId) {
        return hotelRepository.findByHotelIdForUpdate(hotelId)
                .orElseThrow(() -> new HotelSyncNotFoundException(hotelId));
    }

    private static void requireActiveSync(Hotel hotel, UUID token) {
        if (hotel.getSyncStatus() != HotelSyncStatus.SYNCING
                || token == null
                || !token.equals(hotel.getSyncContinuationToken())) {
            throw new HotelSyncConflictException("This hotel sync run is no longer active.");
        }
    }

    public record InitialClaim(String hotelId, String requestedUrl, UUID continuationToken) {
    }

    public record ContinuationClaim(
            String hotelId,
            String requestedUrl,
            String hotelName,
            UUID continuationToken,
            int pageNumber
    ) {
    }

    private record PreviewImport(
            List<Review> newReviews,
            List<HotelSyncReviewPreview> reviewPreviews,
            int newCount,
            int alreadyStoredCount,
            int duplicateCount,
            int beyondBoundaryCount,
            int invalidCount,
            int skippedCount
    ) {
        private boolean overlapFound() {
            return alreadyStoredCount > 0;
        }
    }
}
