package com.tranquility.web;

import com.tranquility.dashboard.persistence.service.HotelCatalogService;
import com.tranquility.dashboard.persistence.service.HotelCollectionService;
import com.tranquility.dashboard.service.HotelSyncService;
import com.tranquility.dashboard.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelCollectionController {

    private final HotelCollectionService collectionService;
    private final HotelCatalogService catalogService;
    private final HotelSyncService syncService;

    @GetMapping
    public List<HotelSummaryResponse> listHotels() {
        return catalogService.listHotels();
    }

    @GetMapping("/{hotelId}")
    public JsonNode getHotel(@PathVariable("hotelId") String hotelId) {
        return catalogService.findHotel(hotelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel snapshot not found."));
    }

    @GetMapping("/{hotelId}/reviews")
    public ReviewCursorPageResponse getReviews(
            @PathVariable("hotelId") String hotelId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return catalogService.findReviews(hotelId, cursor, size)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found."));
    }

    @GetMapping("/{hotelId}/reviews/search")
    public HotelReviewSearchPageResponse searchReviews(
            @PathVariable("hotelId") String hotelId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "all") String sentiment,
            @RequestParam(defaultValue = "date_desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return catalogService.searchReviews(hotelId, search, startDate, endDate, sentiment, sort, page, size)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found."));
    }

    @PostMapping("/collect")
    public HotelCollectionResponse collect(@Valid @RequestBody CollectionRequest request) {
        return collectionService.collect(request.bookingUrls());
    }

    @PostMapping("/{hotelId}/sync")
    public HotelSyncResponse syncHotel(@PathVariable("hotelId") String hotelId) {
        return syncService.syncHotel(hotelId);
    }

    @PostMapping("/{hotelId}/sync/continue")
    public HotelSyncResponse continueHotelSync(
            @PathVariable("hotelId") String hotelId,
            @Valid @RequestBody HotelSyncContinuationRequest request
    ) {
        return syncService.continueSync(hotelId, request.continuationToken());
    }

    @GetMapping("/{hotelId}/sync/pending")
    public HotelSyncResponse pendingHotelSyncPreview(
            @PathVariable("hotelId") String hotelId,
            @RequestParam UUID continuationToken
    ) {
        return syncService.findPendingPreview(hotelId, continuationToken);
    }

    @PostMapping("/{hotelId}/sync/confirm")
    public HotelSyncResponse confirmHotelSyncPreview(
            @PathVariable("hotelId") String hotelId,
            @Valid @RequestBody HotelSyncBatchRequest request
    ) {
        return syncService.confirmSync(hotelId, request.continuationToken(), request.batchId());
    }

    @PostMapping("/{hotelId}/sync/reject")
    public HotelSyncResponse rejectHotelSyncPreview(
            @PathVariable("hotelId") String hotelId,
            @Valid @RequestBody HotelSyncBatchRequest request
    ) {
        return syncService.rejectSync(hotelId, request.continuationToken(), request.batchId());
    }
}
