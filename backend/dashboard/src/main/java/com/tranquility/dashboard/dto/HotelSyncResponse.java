package com.tranquility.dashboard.dto;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

import com.tranquility.dashboard.model.HotelSyncReviewPreview;
import tools.jackson.databind.JsonNode;

public record HotelSyncResponse(
        String hotelId,
        String hotelName,
        Instant nextAllowedAt,
        boolean hotelDetailsUpdated,
        int currentPage,
        int totalPages,
        Integer nextPage,
        int pagesFetched,
        int reviewsReceived,
        int reviewsAdded,
        int reviewsSkipped,
        int totalReviewsAdded,
        int totalReviewsSkipped,
        boolean hasMore,
        boolean unrestrictedSyncRecommended,
        UUID continuationToken,
        String message,
        boolean awaitingConfirmation,
        UUID pendingBatchId,
        JsonNode snapshotPreview,
        List<HotelSyncReviewPreview> reviewPreviews,
        int previewNewReviews,
        int previewAlreadyStoredReviews,
        int previewDuplicateReviews,
        int previewBeyondBoundaryReviews
) {

    public HotelSyncResponse withMessage(String replacementMessage) {
        return new HotelSyncResponse(
                hotelId,
                hotelName,
                nextAllowedAt,
                hotelDetailsUpdated,
                currentPage,
                totalPages,
                nextPage,
                pagesFetched,
                reviewsReceived,
                reviewsAdded,
                reviewsSkipped,
                totalReviewsAdded,
                totalReviewsSkipped,
                hasMore,
                unrestrictedSyncRecommended,
                continuationToken,
                replacementMessage,
                awaitingConfirmation,
                pendingBatchId,
                snapshotPreview,
                reviewPreviews,
                previewNewReviews,
                previewAlreadyStoredReviews,
                previewDuplicateReviews,
                previewBeyondBoundaryReviews);
    }
}
