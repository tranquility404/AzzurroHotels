package com.tranquility.dashboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** Typed records for the JSON snapshot contract returned by the Booking.com collector. */
public final class HotelJsonModel {

    private HotelJsonModel() {
    }

    public record Snapshot(
            String source,
            String collectedAt,
            String requestedUrl,
            String hotelId,
            String hotelName,
            int reviewCount,
            HotelDetails hotelDetails
    ) {
    }

    public record HotelDetails(
            Integer id,
            Integer ufi,
            String name,
            String link,
            @JsonProperty("accommodation_type") String accommodationType,
            @JsonProperty("accommodation_type_id") Integer accommodationTypeId,
            List<JsonNode> brands,
            String description,
            @JsonProperty("fine_print") String finePrint,
            String image,
            Location location,
            Rating rating,
            Policies policies,
            @JsonProperty("hotel_currency") String hotelCurrency,
            @JsonProperty("languages_spoken") List<String> languagesSpoken,
            @JsonProperty("is_sustainable") Boolean isSustainable,
            @JsonProperty("is_work_friendly") Boolean isWorkFriendly,
            List<Facility> facilities,
            List<JsonNode> meals,
            List<Photo> photos,
            List<Room> rooms,
            @JsonProperty("featured_reviews") List<JsonNode> featuredReviews,
            @JsonProperty("questions_and_answers") List<JsonNode> questionsAndAnswers,
            Surroundings surroundings,
            @JsonProperty("page_name") String pageName,
            JsonNode stay,
            @JsonProperty("is_available") JsonNode isAvailable,
            String locale,
            String currency
    ) {
    }

    public record Location(
            String address,
            @JsonProperty("postal_code") String postalCode,
            String city,
            String region,
            String country,
            @JsonProperty("country_code") String countryCode,
            Double latitude,
            Double longitude
    ) {
    }

    public record Rating(
            JsonNode score,
            Integer count,
            Integer stars,
            List<RatingSubscore> subscores
    ) {
    }

    public record RatingSubscore(String name, JsonNode score, Integer count) {
    }

    public record Policies(
            TimeWindow checkin,
            TimeWindow checkout,
            String pets,
            @JsonProperty("allows_children") Boolean allowsChildren,
            @JsonProperty("min_child_age") Integer minChildAge,
            @JsonProperty("has_cribs") Boolean hasCribs,
            @JsonProperty("has_extra_beds") Boolean hasExtraBeds,
            @JsonProperty("accepted_payments") List<String> acceptedPayments
    ) {
    }

    public record TimeWindow(String from, String until) {
    }

    public record Facility(Integer id, String slug, String name) {
    }

    public record Photo(Integer id, String link, String category, String caption) {
    }

    public record Room(
            Integer id,
            String name,
            String description,
            @JsonProperty("size_m2") JsonNode sizeM2,
            @JsonProperty("max_occupancy") Integer maxOccupancy,
            @JsonProperty("max_children") Integer maxChildren,
            List<Bed> beds,
            @JsonProperty("facility_highlights") JsonNode facilityHighlights,
            List<String> photos,
            @JsonProperty("is_available") Boolean isAvailable,
            List<JsonNode> offers
    ) {
    }

    public record Bed(String type, Integer count) {
    }

    public record Surroundings(
            List<NearbyPlace> landmarks,
            List<NearbyPlace> restaurants,
            @JsonProperty("public_transport") List<NearbyPlace> publicTransport,
            List<NearbyPlace> airports,
            List<NearbyPlace> beaches,
            @JsonProperty("natural_beauty") List<NearbyPlace> naturalBeauty
    ) {
    }

    public record NearbyPlace(
            String name,
            String type,
            String distance,
            Double latitude,
            Double longitude,
            @JsonProperty("walk_time") String walkTime,
            @JsonProperty("drive_time") String driveTime
    ) {
    }

    public record Guest(
            String name,
            @JsonProperty("country_code") String countryCode,
            String country,
            String type,
            @JsonProperty("review_count") Integer reviewCount,
            @JsonProperty("member_since") String memberSince,
            String avatar,
            @JsonProperty("is_anonymous") Boolean isAnonymous
    ) {
    }

    public record Stay(
            String checkin,
            String checkout,
            Integer nights,
            @JsonProperty("room_id") Integer roomId,
            @JsonProperty("room_name") String roomName,
            String status
    ) {
    }
}
