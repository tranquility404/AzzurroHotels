package com.tranquility.dashboard.persistence.entity;

import com.tranquility.dashboard.model.HotelSyncStatus;
import com.tranquility.dashboard.model.HotelJsonModel;
import com.tranquility.shared.model.AuditableEntity;
import com.tranquility.shared.storage.model.StorageBucket;
import com.tranquility.shared.storage.model.StorageLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "hotels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hotel extends AuditableEntity {

    @Id
    @Column(name = "hotel_id", nullable = false, updatable = false, columnDefinition = "text")
    private String hotelId;

    @Column(nullable = false, columnDefinition = "text")
    private String source;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "collected_at_text", nullable = false, columnDefinition = "text")
    private String collectedAtText;

    @Column(name = "requested_url", nullable = false, columnDefinition = "text")
    private String requestedUrl;

    @Column(name = "hotel_name", nullable = false, columnDefinition = "text")
    private String hotelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_bucket", nullable = false)
    private StorageBucket snapshotBucket;

    @Column(name = "snapshot_path", nullable = false, columnDefinition = "text")
    private String snapshotPath;

    @Column(name = "snapshot_object_id", nullable = false, columnDefinition = "text")
    private String snapshotObjectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hotel_details", nullable = false, columnDefinition = "jsonb")
    private HotelJsonModel.HotelDetails hotelDetails;

    @Column(name = "last_sync_started_at")
    private Instant lastSyncStartedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 32)
    private HotelSyncStatus syncStatus = HotelSyncStatus.IDLE;

    @Column(name = "sync_continuation_token")
    private UUID syncContinuationToken;

    @Column(name = "sync_next_page")
    private Integer syncNextPage;

    @Column(name = "sync_total_pages")
    private Integer syncTotalPages;

    @Column(name = "sync_source_page_start")
    private Integer syncSourcePageStart;

    @Column(name = "sync_pages_fetched", nullable = false)
    private int syncPagesFetched;

    @Column(name = "sync_reviews_added", nullable = false)
    private int syncReviewsAdded;

    @Column(name = "sync_reviews_skipped", nullable = false)
    private int syncReviewsSkipped;

    @Version
    @Column(nullable = false)
    private Long version;

    public static Hotel fromSnapshot(HotelJsonModel.Snapshot snapshot) {
        Hotel hotel = new Hotel();
        hotel.replaceWith(snapshot);
        return hotel;
    }

    public void replaceWith(HotelJsonModel.Snapshot snapshot) {
        this.hotelId = snapshot.hotelId();
        this.source = snapshot.source();
        this.collectedAtText = snapshot.collectedAt();
        this.collectedAt = Instant.parse(snapshot.collectedAt());
        this.requestedUrl = snapshot.requestedUrl();
        this.hotelName = snapshot.hotelName();
        this.hotelDetails = snapshot.hotelDetails();
    }

    public void setSnapshotLocation(StorageLocation location) {
        this.snapshotBucket = location.bucket();
        this.snapshotPath = location.path();
        this.snapshotObjectId = location.objectId();
    }

    public void beginSync(Instant startedAt, UUID continuationToken) {
        this.lastSyncStartedAt = startedAt;
        this.syncStatus = HotelSyncStatus.SYNCING;
        this.syncContinuationToken = continuationToken;
        this.syncNextPage = null;
        this.syncTotalPages = null;
        this.syncSourcePageStart = null;
        this.syncPagesFetched = 0;
        this.syncReviewsAdded = 0;
        this.syncReviewsSkipped = 0;
    }

    public void claimSyncContinuation(UUID continuationToken) {
        requireContinuation(continuationToken, HotelSyncStatus.AWAITING_CONTINUATION);
        this.syncStatus = HotelSyncStatus.SYNCING;
    }

    public void awaitSyncConfirmation(UUID continuationToken) {
        requireContinuation(continuationToken, HotelSyncStatus.SYNCING);
        this.syncStatus = HotelSyncStatus.AWAITING_CONFIRMATION;
    }

    public void awaitSyncConfirmation(UUID continuationToken, int sourcePageStart, int totalPages) {
        requireContinuation(continuationToken, HotelSyncStatus.SYNCING);
        this.syncStatus = HotelSyncStatus.AWAITING_CONFIRMATION;
        this.syncSourcePageStart = sourcePageStart;
        this.syncTotalPages = totalPages;
    }

    public void beginSyncConfirmation(UUID continuationToken) {
        requireContinuation(continuationToken, HotelSyncStatus.AWAITING_CONFIRMATION);
        this.syncStatus = HotelSyncStatus.SYNCING;
    }

    public void cancelSync(UUID continuationToken) {
        requireContinuation(continuationToken, HotelSyncStatus.AWAITING_CONFIRMATION);
        this.syncStatus = HotelSyncStatus.CANCELLED;
        this.syncContinuationToken = null;
        this.syncNextPage = null;
    }

    public void awaitSyncContinuation(
            UUID continuationToken,
            int sourcePageStart,
            int nextPage,
            int totalPages,
            int pagesFetched,
            int reviewsAdded,
            int reviewsSkipped
    ) {
        requireContinuation(continuationToken, HotelSyncStatus.SYNCING);
        this.syncStatus = HotelSyncStatus.AWAITING_CONTINUATION;
        this.syncSourcePageStart = sourcePageStart;
        this.syncNextPage = nextPage;
        this.syncTotalPages = totalPages;
        this.syncPagesFetched = pagesFetched;
        this.syncReviewsAdded = reviewsAdded;
        this.syncReviewsSkipped = reviewsSkipped;
    }

    public void completeSync(
            UUID continuationToken,
            int sourcePageStart,
            int totalPages,
            int pagesFetched,
            int reviewsAdded,
            int reviewsSkipped
    ) {
        requireContinuation(continuationToken, HotelSyncStatus.SYNCING);
        this.syncStatus = HotelSyncStatus.COMPLETE;
        this.syncContinuationToken = null;
        this.syncNextPage = null;
        this.syncSourcePageStart = sourcePageStart;
        this.syncTotalPages = totalPages;
        this.syncPagesFetched = pagesFetched;
        this.syncReviewsAdded = reviewsAdded;
        this.syncReviewsSkipped = reviewsSkipped;
    }

    public void recordContinuationFailure(UUID continuationToken) {
        if (syncStatus == HotelSyncStatus.SYNCING && Objects.equals(continuationToken, syncContinuationToken)) {
            this.syncStatus = HotelSyncStatus.AWAITING_CONTINUATION;
        }
    }

    public void failSync(UUID continuationToken) {
        if (syncStatus == HotelSyncStatus.SYNCING && Objects.equals(continuationToken, syncContinuationToken)) {
            this.syncStatus = HotelSyncStatus.FAILED;
            this.syncContinuationToken = null;
            this.syncNextPage = null;
        }
    }

    private void requireContinuation(UUID continuationToken, HotelSyncStatus expectedStatus) {
        if (syncStatus != expectedStatus || !Objects.equals(continuationToken, syncContinuationToken)) {
            throw new IllegalStateException("The hotel sync run is no longer active.");
        }
    }
}
