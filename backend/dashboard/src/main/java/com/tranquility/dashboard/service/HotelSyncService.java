package com.tranquility.dashboard.service;

import com.tranquility.collection.HotelDataCollectionException;
import com.tranquility.collection.HotelDataCollector;
import com.tranquility.collection.HotelReviewPageCollector;
import com.tranquility.collection.model.HotelData;
import com.tranquility.collection.model.HotelReviewPage;
import com.tranquility.dashboard.mapper.HotelSnapshotFactory;
import com.tranquility.dashboard.dto.HotelSyncResponse;
import com.tranquility.dashboard.exception.HotelSyncConflictException;
import com.tranquility.dashboard.exception.HotelSyncNotFoundException;
import com.tranquility.dashboard.exception.HotelSyncProviderException;
import com.tranquility.dashboard.persistence.entity.HotelSyncPendingBatch;
import com.tranquility.dashboard.persistence.service.HotelSyncStateService;
import com.tranquility.shared.storage.model.UploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HotelSyncService {

    private final HotelDataCollector hotelDataCollector;
    private final HotelReviewPageCollector reviewPageCollector;
    private final HotelSnapshotFactory snapshotFactory;
    private final HotelSnapshotArchiveService snapshotArchiveService;
    private final HotelSyncStateService stateService;

    public HotelSyncResponse syncHotel(String hotelId) {
        HotelSyncStateService.InitialClaim claim = stateService.claimInitialSync(hotelId);
        try {
            HotelData hotelData = hotelDataCollector.collect(claim.requestedUrl());
            if (!hotelId.equals(hotelData.hotelId())) {
                throw new HotelSyncProviderException(
                        "The provider returned a different hotel than the one requested.", null);
            }
            HotelSnapshotFactory.SnapshotDocument snapshot = snapshotFactory.create(hotelData);
            HotelReviewPage firstPage = reviewPageCollector.collectReviews(hotelId, 1);
            HotelSyncResponse staged = stateService.stageInitialPage(
                    claim, hotelData.hotelName(), snapshot.snapshot(), firstPage);
            return autoConfirmWhenNoNewReviews(hotelId, staged);
        } catch (RuntimeException exception) {
            Instant nextAllowedAt = stateService.failInitialSync(hotelId, claim.continuationToken());
            if (exception instanceof HotelSyncProviderException syncException) {
                throw new HotelSyncProviderException(syncException.getMessage(), syncException, nextAllowedAt);
            }
            if (exception instanceof HotelSyncConflictException conflictException) {
                throw conflictException;
            }
            if (exception instanceof HotelSyncNotFoundException notFoundException) {
                throw notFoundException;
            }
            if (exception instanceof HotelDataCollectionException collectionException) {
                throw new HotelSyncProviderException(
                        collectionException.getMessage(), collectionException, nextAllowedAt);
            }
            throw new HotelSyncProviderException(
                    "The hotel could not be fully synced. The attempt has been recorded; retry after the daily limit resets.",
                    exception,
                    nextAllowedAt);
        }
    }

    public HotelSyncResponse continueSync(String hotelId, UUID continuationToken) {
        HotelSyncStateService.ContinuationClaim claim =
                stateService.claimContinuation(hotelId, continuationToken);
        try {
            HotelReviewPage page = reviewPageCollector.collectReviews(hotelId, claim.pageNumber());
            HotelSyncResponse staged = stateService.stageContinuationPage(claim, page);
            return autoConfirmWhenNoNewReviews(hotelId, staged);
        } catch (RuntimeException exception) {
            stateService.releaseContinuation(hotelId, continuationToken);
            if (exception instanceof HotelSyncProviderException syncException) {
                throw syncException;
            }
            if (exception instanceof HotelSyncConflictException conflictException) {
                throw conflictException;
            }
            if (exception instanceof HotelSyncNotFoundException notFoundException) {
                throw notFoundException;
            }
            if (exception instanceof HotelDataCollectionException collectionException) {
                throw new HotelSyncProviderException(collectionException.getMessage(), collectionException);
            }
            throw exception;
        }
    }

    public HotelSyncResponse findPendingPreview(String hotelId, UUID continuationToken) {
        return stateService.findPendingPreview(hotelId, continuationToken);
    }

    public HotelSyncResponse confirmSync(String hotelId, UUID continuationToken, UUID batchId) {
        HotelSyncPendingBatch batch = stateService.pendingBatchForConfirmation(hotelId, continuationToken, batchId);
        JsonNode snapshot = batch.snapshotCopy();
        UploadResult uploadResult = snapshot == null
                ? null
                : snapshotArchiveService.uploadVersionedSnapshot(snapshot, batchId);
        return stateService.confirmPendingBatch(hotelId, continuationToken, batchId, uploadResult);
    }

    public HotelSyncResponse rejectSync(String hotelId, UUID continuationToken, UUID batchId) {
        return stateService.rejectPendingBatch(hotelId, continuationToken, batchId);
    }

    private HotelSyncResponse autoConfirmWhenNoNewReviews(String hotelId, HotelSyncResponse staged) {
        if (!staged.awaitingConfirmation() || staged.previewNewReviews() > 0) {
            return staged;
        }

        try {
            return confirmSync(hotelId, staged.continuationToken(), staged.pendingBatchId());
        } catch (HotelSyncConflictException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return staged.withMessage(
                    "No new reviews found, but this page could not be finalized automatically. "
                            + "Confirm the preview to retry.");
        }
    }
}
