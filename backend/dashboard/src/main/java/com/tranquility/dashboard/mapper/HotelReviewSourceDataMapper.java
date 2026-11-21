package com.tranquility.dashboard.mapper;

import com.tranquility.dashboard.persistence.entity.Hotel;
import com.tranquility.dashboard.persistence.entity.Review;
import com.tranquility.dashboard.model.HotelJsonModel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

@Component
public class HotelReviewSourceDataMapper {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public String sourceReviewId(JsonNode sourceData) {
        JsonNode id = sourceData == null ? null : sourceData.get("id");
        return id != null && id.isString() && !id.asString().isBlank() ? id.asString().trim() : null;
    }

    public Review toReview(Hotel hotel, int sourcePageNumber, int position, JsonNode sourceData) {
        String sourceReviewId = sourceReviewId(sourceData);
        if (sourceReviewId == null) {
            throw new IllegalArgumentException("A source review id is required to save a hotel review.");
        }

        return Review.fromSync(
                hotel,
                sourcePageNumber,
                position,
                sourceReviewId,
                integer(sourceData, "score"),
                text(sourceData, "title"),
                text(sourceData, "positive"),
                text(sourceData, "negative"),
                text(sourceData, "language"),
                text(sourceData, "date"),
                integer(sourceData, "helpful_votes"),
                bool(sourceData, "is_approved"),
                strings(sourceData.get("photos")),
                model(sourceData.get("guest"), HotelJsonModel.Guest.class),
                model(sourceData.get("stay"), HotelJsonModel.Stay.class),
                text(sourceData, "owner_reply"),
                sourceData);
    }

    private <T> T model(JsonNode node, Class<T> type) {
        if (node == null || !node.isObject()) {
            return null;
        }
        try {
            return jsonMapper.treeToValue(node, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String text(JsonNode node, String property) {
        JsonNode value = node.get(property);
        return value != null && value.isString() ? value.asString() : null;
    }

    private static Integer integer(JsonNode node, String property) {
        JsonNode value = node.get(property);
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    private static Boolean bool(JsonNode node, String property) {
        JsonNode value = node.get(property);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isString()) {
                values.add(value.asString());
            }
        });
        return values;
    }
}
