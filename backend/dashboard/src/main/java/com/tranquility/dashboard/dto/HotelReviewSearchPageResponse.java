package com.tranquility.dashboard.dto;

import com.tranquility.dashboard.persistence.entity.Review;
import org.springframework.data.domain.Page;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

public record HotelReviewSearchPageResponse(
        List<JsonNode> data,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public HotelReviewSearchPageResponse {
        data = List.copyOf(data);
    }

    public static HotelReviewSearchPageResponse from(Page<Review> page) {
        List<JsonNode> data = page.getContent().stream()
                .map(HotelReviewSearchPageResponse::withDatabaseReviewId)
                .toList();
        return new HotelReviewSearchPageResponse(
                data,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }

    private static JsonNode withDatabaseReviewId(Review review) {
        ObjectNode data = (ObjectNode) review.getSourceData().deepCopy();
        data.put("reviewId", review.getId().toString());
        return data;
    }
}
