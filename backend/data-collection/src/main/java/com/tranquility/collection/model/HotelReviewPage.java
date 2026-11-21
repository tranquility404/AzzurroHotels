package com.tranquility.collection.model;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record HotelReviewPage(
        int currentPage,
        int totalPages,
        List<JsonNode> results
) {

    public HotelReviewPage {
        results = results == null
                ? List.of()
                : results.stream().filter(result -> result != null).map(JsonNode::deepCopy).toList();
    }

    public boolean hasNext() {
        return currentPage < totalPages;
    }
}
