package com.tranquility.dashboard.mapper;

import com.tranquility.dashboard.persistence.entity.Hotel;
import com.tranquility.dashboard.model.HotelJsonModel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class HotelSnapshotMapper {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public HotelJsonModel.Snapshot parse(JsonNode snapshot) {
        try {
            return jsonMapper.treeToValue(snapshot, HotelJsonModel.Snapshot.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("The hotel snapshot does not match the stored JSON contract.", exception);
        }
    }

    public JsonNode toJson(Hotel hotel, long reviewCount) {
        ObjectNode snapshot = jsonMapper.createObjectNode();
        snapshot.put("source", hotel.getSource());
        snapshot.put("collectedAt", hotel.getCollectedAtText());
        snapshot.put("requestedUrl", hotel.getRequestedUrl());
        snapshot.put("hotelId", hotel.getHotelId());
        snapshot.put("hotelName", hotel.getHotelName());
        snapshot.put("reviewCount", reviewCount);
        snapshot.set("hotelDetails", jsonMapper.valueToTree(hotel.getHotelDetails()));
        return snapshot;
    }
}
