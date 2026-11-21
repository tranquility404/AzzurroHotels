package com.tranquility.dashboard.persistence.entity;

import com.tranquility.dashboard.model.HotelJsonModel;
import com.tranquility.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reviews", uniqueConstraints = {
        @UniqueConstraint(
                name = "uq_reviews_hotel_page_source_id",
                columnNames = {"hotel_id", "source_page_number", "source_review_id"}),
        @UniqueConstraint(
                name = "uq_reviews_hotel_page_position",
                columnNames = {"hotel_id", "source_page_number", "position"})
})
public class Review extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(name = "source_page_number", nullable = false)
    private int sourcePageNumber;

    @Column(name = "source_review_id", nullable = false, columnDefinition = "text")
    private String sourceReviewId;

    @Column(nullable = false)
    private int position;

    private Integer score;

    @Column(columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String positive;

    @Column(columnDefinition = "text")
    private String negative;

    @Column(columnDefinition = "text")
    private String language;

    @Column(columnDefinition = "text")
    private String date;

    @Column(name = "helpful_votes")
    private Integer helpfulVotes;

    @Column(name = "is_approved")
    private Boolean isApproved;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> photos;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private HotelJsonModel.Guest guest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private HotelJsonModel.Stay stay;

    @Column(name = "owner_reply", columnDefinition = "text")
    private String ownerReply;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_data", nullable = false, columnDefinition = "jsonb")
    private JsonNode sourceData;

    public static Review fromSync(
            Hotel hotel,
            int sourcePageNumber,
            int sourcePosition,
            String sourceReviewId,
            Integer score,
            String title,
            String positive,
            String negative,
            String language,
            String date,
            Integer helpfulVotes,
            Boolean approved,
            List<String> photos,
            HotelJsonModel.Guest guest,
            HotelJsonModel.Stay stay,
            String ownerReply,
            JsonNode sourceData
    ) {
        Review review = new Review();
        review.hotel = Objects.requireNonNull(hotel);
        review.sourcePageNumber = sourcePageNumber;
        review.position = sourcePosition;
        review.sourceReviewId = Objects.requireNonNull(sourceReviewId);
        review.score = score;
        review.title = title;
        review.positive = positive;
        review.negative = negative;
        review.language = language;
        review.date = date;
        review.helpfulVotes = helpfulVotes;
        review.isApproved = approved;
        review.photos = photos;
        review.guest = guest;
        review.stay = stay;
        review.ownerReply = ownerReply;
        review.sourceData = Objects.requireNonNull(sourceData).deepCopy();
        return review;
    }
}
