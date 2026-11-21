package com.tranquility.dashboard.persistence.service;

import com.tranquility.collection.BookingHotelUrlValidator;
import com.tranquility.collection.HotelDataCollectionException;
import com.tranquility.collection.HotelDataCollector;
import com.tranquility.collection.model.HotelData;
import com.tranquility.dashboard.mapper.HotelSnapshotFactory;
import com.tranquility.dashboard.dto.HotelCollectionItemResponse;
import com.tranquility.dashboard.dto.HotelCollectionResponse;
import com.tranquility.dashboard.persistence.repository.ReviewRepository;
import com.tranquility.dashboard.service.HotelSnapshotArchiveService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HotelCollectionService {

    private static final Logger logger = LoggerFactory.getLogger(HotelCollectionService.class);

    private final HotelDataCollector dataCollector;
    private final HotelSnapshotFactory snapshotFactory;
    private final HotelSnapshotArchiveService archiveService;
    private final ReviewRepository reviewRepository;

    public HotelCollectionResponse collect(List<String> bookingUrls) {
        List<HotelCollectionItemResponse> results = new ArrayList<>();
        Set<String> submittedProperties = new HashSet<>();

        for (String bookingUrl : bookingUrls) {
            String validatedUrl;
            String propertyKey;
            try {
                validatedUrl = BookingHotelUrlValidator.validate(bookingUrl);
                propertyKey = BookingHotelUrlValidator.canonicalKey(validatedUrl);
            } catch (IllegalArgumentException exception) {
                results.add(HotelCollectionItemResponse.failed(bookingUrl, exception.getMessage()));
                continue;
            }

            if (!submittedProperties.add(propertyKey)) {
                results.add(HotelCollectionItemResponse.skipped(validatedUrl));
                continue;
            }

            try {
                HotelData data = dataCollector.collect(validatedUrl);
                HotelSnapshotFactory.SnapshotDocument snapshot = snapshotFactory.create(data);
                archiveService.archive(snapshot.fileName(), snapshot.snapshot());
                results.add(success(data, snapshot.fileName(), reviewRepository.countForHotel(data.hotelId())));
            } catch (HotelDataCollectionException exception) {
                logger.warn("Hotel collection failed: {}", exception.getMessage());
                results.add(HotelCollectionItemResponse.failed(validatedUrl, exception.getMessage()));
            } catch (RuntimeException exception) {
                logger.error("Hotel collection failed with {}", exception.getClass().getSimpleName());
                results.add(HotelCollectionItemResponse.failed(validatedUrl,
                        "Hotel data was collected but could not be archived and saved. Check the backend logs."));
            }
        }

        int successCount = (int) results.stream().filter(result -> "SAVED".equals(result.status())).count();
        int failureCount = (int) results.stream().filter(result -> "FAILED".equals(result.status())).count();
        int skippedCount = (int) results.stream().filter(result -> "SKIPPED".equals(result.status())).count();
        return new HotelCollectionResponse(results, successCount, failureCount, skippedCount);
    }

    private HotelCollectionItemResponse success(HotelData data, String fileName, long reviewCount) {
        JsonNode ratingNode = data.hotelDetails().path("rating").path("score");
        var ratingValue = ratingNode.asDoubleOpt();
        Double rating = ratingValue.isPresent() ? ratingValue.getAsDouble() : null;
        return new HotelCollectionItemResponse("SAVED", data.requestedUrl(), data.hotelId(), data.hotelName(),
                rating, reviewCount, 0, fileName,
                "Hotel snapshot uploaded to storage and saved to the database.");
    }
}
