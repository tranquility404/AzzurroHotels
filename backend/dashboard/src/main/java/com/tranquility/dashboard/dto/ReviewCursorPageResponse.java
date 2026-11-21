package com.tranquility.dashboard.dto;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record ReviewCursorPageResponse(List<JsonNode> data, String nextCursor, boolean hasNext) {

    public ReviewCursorPageResponse {
        data = List.copyOf(data);
    }
}
