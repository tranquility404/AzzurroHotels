package com.tranquility.dashboard.mapper;

import com.tranquility.collection.model.HotelData;
import com.tranquility.dashboard.service.HotelSnapshotArchiveService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

/** Builds a hotel-details snapshot in memory without writing a local file. */
@Component
public class HotelSnapshotFactory {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public SnapshotDocument create(HotelData data) {
        String fileName = HotelSnapshotArchiveService.safeHotelFileName(data.hotelId());
        ObjectNode snapshot = jsonMapper.createObjectNode();
        snapshot.put("source", "Booking.com");
        snapshot.put("collectedAt", Instant.now().toString());
        snapshot.put("requestedUrl", data.requestedUrl());
        snapshot.put("hotelId", data.hotelId());
        snapshot.put("hotelName", data.hotelName());
        snapshot.put("reviewCount", data.reviewCount());
        snapshot.set("hotelDetails", data.hotelDetails());
        return new SnapshotDocument(fileName, snapshot);
    }

    public record SnapshotDocument(String fileName, JsonNode snapshot) {
    }
}
