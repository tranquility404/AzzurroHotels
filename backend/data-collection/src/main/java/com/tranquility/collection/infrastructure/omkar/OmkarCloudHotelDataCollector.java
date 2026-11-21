package com.tranquility.collection.infrastructure.omkar;

import com.tranquility.collection.BookingHotelUrlValidator;
import com.tranquility.collection.HotelDataCollectionException;
import com.tranquility.collection.HotelDataCollector;
import com.tranquility.collection.HotelReviewPageCollector;
import com.tranquility.collection.model.HotelData;
import com.tranquility.collection.model.HotelReviewPage;
import com.tranquility.collection.infrastructure.omkar.config.OmkarProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OmkarCloudHotelDataCollector implements HotelDataCollector, HotelReviewPageCollector {

    private final RestClient restClient;
    private final OmkarProperties properties;

    @Override
    public HotelData collect(String bookingHotelUrl) {
        String validatedUrl = BookingHotelUrlValidator.validate(bookingHotelUrl);
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new HotelDataCollectionException("Omkar Cloud API key is missing. Set OMKAR_API_KEY in your environment.");
        }

        JsonNode hotelDetails = fetchHotelDetails(validatedUrl);
        String hotelId = hotelDetails.path("id").asText("");
        String hotelName = hotelDetails.path("name").asText("");
        if (hotelId.isBlank() || hotelName.isBlank()) {
            throw new HotelDataCollectionException("Omkar Cloud returned hotel details without an id or name.");
        }

        int reviewCount = hotelDetails.path("rating").path("count").asInt(0);
        return new HotelData(validatedUrl, hotelId, hotelName, hotelDetails, reviewCount);
    }

    private JsonNode fetchHotelDetails(String bookingHotelUrl) {
        return get("/booking/hotels/details", Map.of("hotel", bookingHotelUrl));
    }

    @Override
    public HotelReviewPage collectReviews(String hotelId, int pageNumber) {
        if (hotelId == null || hotelId.isBlank()) {
            throw new HotelDataCollectionException("A hotel id is required to collect reviews.");
        }
        if (pageNumber < 1 || pageNumber > 400) {
            throw new HotelDataCollectionException("Omkar Cloud review pages must be between 1 and 400.");
        }

        JsonNode response = get("/booking/hotels/reviews", Map.of(
                "query", hotelId,
                "page", String.valueOf(pageNumber)));
        JsonNode results = response.path("results");
        int currentPage = response.path("current_page").asInt(-1);
        int totalPages = response.path("total_pages").asInt(-1);
        if (!results.isArray()
                || currentPage != pageNumber
                || totalPages < 0
                || totalPages > 400
                || (totalPages != 0 && totalPages < currentPage)) {
            throw new HotelDataCollectionException("Omkar Cloud returned an invalid hotel review page.");
        }

        List<JsonNode> reviews = results.valueStream().toList();
        return new HotelReviewPage(currentPage, totalPages, reviews);
    }

    private JsonNode get(String path, Map<String, String> queryParameters) {
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path(path);
                        queryParameters.forEach((name, value) -> builder.queryParam(name, value));
                        builder.queryParam("locale", properties.getLocale());
                        return builder.build();
                    })
                    .header("API-Key", properties.getApiKey())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.isObject()) {
                throw new HotelDataCollectionException("Omkar Cloud returned an empty or invalid JSON response.");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new HotelDataCollectionException("Omkar Cloud returned HTTP " + exception.getStatusCode().value() + ". Check the API key, plan quota, and hotel URL.", exception);
        } catch (RestClientException exception) {
            throw new HotelDataCollectionException("Could not reach Omkar Cloud. Try again shortly.", exception);
        }
    }
}
