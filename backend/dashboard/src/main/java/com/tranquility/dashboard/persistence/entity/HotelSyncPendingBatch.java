package com.tranquility.dashboard.persistence.entity;

import com.tranquility.collection.model.HotelReviewPage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "hotel_sync_pending_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HotelSyncPendingBatch {

    @Id
    @Column(name = "batch_id", nullable = false, updatable = false)
    private UUID batchId;

    @Column(name = "hotel_id", nullable = false, columnDefinition = "text")
    private String hotelId;

    @Column(name = "continuation_token", nullable = false)
    private UUID continuationToken;

    @Column(name = "current_page", nullable = false)
    private int currentPage;

    @Column(name = "total_pages", nullable = false)
    private int totalPages;

    @Column(name = "source_page_start", nullable = false)
    private int sourcePageStart;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", columnDefinition = "jsonb")
    private JsonNode snapshotJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_results", nullable = false, columnDefinition = "jsonb")
    private JsonNode reviewResults;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public HotelSyncPendingBatch(
            UUID batchId,
            String hotelId,
            UUID continuationToken,
            HotelReviewPage page,
            int sourcePageStart,
            JsonNode snapshotJson,
            JsonNode reviewResults,
            Instant createdAt
    ) {
        this.batchId = batchId;
        this.hotelId = hotelId;
        this.continuationToken = continuationToken;
        this.currentPage = page.currentPage();
        this.totalPages = page.totalPages();
        this.sourcePageStart = sourcePageStart;
        this.snapshotJson = snapshotJson == null ? null : snapshotJson.deepCopy();
        this.reviewResults = reviewResults.deepCopy();
        this.createdAt = createdAt;
    }

    public HotelReviewPage toReviewPage() {
        List<JsonNode> results = new ArrayList<>();
        reviewResults.forEach(result -> results.add(result.deepCopy()));
        return new HotelReviewPage(currentPage, totalPages, results);
    }

    public JsonNode snapshotCopy() {
        return snapshotJson == null ? null : snapshotJson.deepCopy();
    }
}
