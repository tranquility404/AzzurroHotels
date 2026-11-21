package com.tranquility.insights.persistence.service;

import com.tranquility.insights.model.hotels.*;
import com.tranquility.insights.model.hotels.*;
import com.tranquility.insights.persistence.repository.HotelProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class HotelProfileService {

    private static final Pattern HOTEL_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final HotelProfileRepository repository;

    public HotelProfileService(HotelProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public String findHotels(String searchTerm) {
        String normalized = searchTerm == null ? "" : searchTerm.trim();
        if (normalized.length() > 120) {
            return "Search text is too long. Use at most 120 characters.";
        }

        List<HotelCandidate> hotels = repository.searchHotels(normalized);
        if (hotels.isEmpty()) {
            return "No matching hotels were found.";
        }

        StringBuilder result = new StringBuilder("Hotel choices (up to 10; choose one before property details):\n");
        for (HotelCandidate hotel : hotels) {
            result.append("- ").append(hotel.hotelName()).append(" (hotel id ").append(hotel.hotelId())
                    .append("); location: ").append(value(hotel.location()))
                    .append("; rating: ").append(value(hotel.overallRating()));
            if (present(hotel.ratingCount())) {
                result.append(" from ").append(hotel.ratingCount()).append(" ratings");
            }
            result.append("; stored review count: ").append(hotel.reviewCount());
            if (present(hotel.nearbyMatches())) {
                result.append("; matching nearby places: ").append(hotel.nearbyMatches());
            }
            result.append('\n');
        }
        return result.toString().trim();
    }

    @Transactional(readOnly = true)
    public String getLocation(String requestedHotelId) {
        String hotelId = validatedExistingHotelId(requestedHotelId);
        if (hotelId == null) return "Choose one hotel from find_hotels before requesting its location.";

        return repository.findLocation(hotelId).stream().findFirst()
                .map(location -> "Stored location for " + location.hotelName() + ": "
                        + joined(location.address(), location.city(), location.region(), location.postalCode(), location.country())
                        + coordinates(location.latitude(), location.longitude()))
                .orElse("No location details are stored for that hotel.");
    }

    @Transactional(readOnly = true)
    public String getHotelLocationInventory() {
        List<HotelLocation> locations = repository.findHotelLocationInventory();
        if (locations.isEmpty()) return "No hotel locations are recorded.";

        StringBuilder result = new StringBuilder("Compact location inventory for ")
                .append(locations.size())
                .append(locations.size() == 1 ? " recorded property" : " recorded properties")
                .append(". This contains location fields only, not full hotel profiles.\n");
        for (HotelLocation location : locations) {
            result.append("- ").append(value(location.hotelName())).append(" (hotel id ")
                    .append(value(location.hotelId())).append("): ")
                    .append(joined(location.address(), location.city(), location.region(),
                            location.postalCode(), location.country()));
            if (present(location.latitude()) && present(location.longitude())) {
                result.append("; coordinates ").append(location.latitude()).append(", ").append(location.longitude());
            }
            result.append('\n');
        }
        return result.toString().trim();
    }

    @Transactional(readOnly = true)
    public String getNearbyPlaces(String requestedHotelId, String searchTerm) {
        String hotelId = validatedExistingHotelId(requestedHotelId);
        if (hotelId == null) return "Choose one hotel from find_hotels before requesting nearby places.";
        String normalized = searchTerm == null ? "" : searchTerm.trim();
        if (normalized.length() > 100) return "Nearby-place search text must be at most 100 characters.";

        List<HotelNearbyPlace> places = repository.findNearbyPlaces(hotelId, normalized);
        if (places.isEmpty()) return "No matching nearby places are stored for that hotel.";

        StringBuilder result = new StringBuilder("Nearby places for hotel id ").append(hotelId)
                .append(" (up to 20):\n");
        for (HotelNearbyPlace place : places) {
            result.append("- ").append(value(place.name())).append(" [").append(value(place.category()));
            if (present(place.type())) result.append(", ").append(place.type());
            result.append(']');
            if (present(place.distance())) result.append("; distance ").append(place.distance());
            if (present(place.walkTime())) result.append("; walk ").append(place.walkTime());
            if (present(place.driveTime())) result.append("; drive ").append(place.driveTime());
            result.append('\n');
        }
        return result.toString().trim();
    }

    @Transactional(readOnly = true)
    public String getFacilities(String requestedHotelId) {
        String hotelId = validatedExistingHotelId(requestedHotelId);
        if (hotelId == null) return "Choose one hotel from find_hotels before requesting its facilities.";

        List<HotelFacility> facilities = repository.findFacilities(hotelId);
        if (facilities.isEmpty()) return "No facility details are stored for that hotel.";
        return "Facilities recorded for hotel id " + hotelId + " (up to 30): "
                + facilities.stream().map(HotelFacility::name).filter(HotelProfileService::present)
                .map(String::trim).distinct().reduce((left, right) -> left + "; " + right).orElse("none");
    }

    @Transactional(readOnly = true)
    public String getRooms(String requestedHotelId) {
        String hotelId = validatedExistingHotelId(requestedHotelId);
        if (hotelId == null) return "Choose one hotel from find_hotels before requesting its room options.";

        List<HotelRoomOption> rooms = repository.findRoomOptions(hotelId);
        if (rooms.isEmpty()) return "No room options are stored for that hotel.";

        StringBuilder result = new StringBuilder("Room options recorded for hotel id ").append(hotelId)
                .append(" (up to 12; this is not live availability):\n");
        for (HotelRoomOption room : rooms) {
            result.append("- ").append(value(room.name()));
            if (present(room.sizeSquareMeters())) result.append("; size ").append(room.sizeSquareMeters()).append(" m²");
            if (present(room.maxOccupancy())) result.append("; max occupancy ").append(room.maxOccupancy());
            if (present(room.maxChildren())) result.append("; max children ").append(room.maxChildren());
            if (present(room.beds())) result.append("; beds: ").append(room.beds());
            if (present(room.description())) result.append("; ").append(room.description());
            if (present(room.facilityHighlights())) result.append("; highlights: ").append(room.facilityHighlights());
            result.append('\n');
        }
        return result.toString().trim();
    }

    @Transactional(readOnly = true)
    public String getPolicies(String requestedHotelId) {
        String hotelId = validatedExistingHotelId(requestedHotelId);
        if (hotelId == null) return "Choose one hotel from find_hotels before requesting its policies.";

        return repository.findPolicies(hotelId).stream().findFirst()
                .map(policy -> "Stored policies for " + policy.hotelName() + ": check-in "
                        + window(policy.checkinFrom(), policy.checkinUntil()) + "; check-out "
                        + window(policy.checkoutFrom(), policy.checkoutUntil()) + "; pets " + value(policy.pets())
                        + "; children allowed " + value(policy.allowsChildren())
                        + "; minimum child age " + value(policy.minimumChildAge())
                        + "; cribs " + value(policy.cribsAvailable())
                        + "; extra beds " + value(policy.extraBedsAvailable())
                        + "; accepted payments (up to 10) " + value(policy.acceptedPayments())
                        + (present(policy.finePrint()) ? "; terms: " + policy.finePrint() : ""))
                .orElse("No policy details are stored for that hotel.");
    }

    @Transactional(readOnly = true)
    public String getRatings(String requestedHotelId) {
        String hotelId = validatedExistingHotelId(requestedHotelId);
        if (hotelId == null) return "Choose one hotel from find_hotels before requesting its ratings.";

        return repository.findRatings(hotelId).stream().findFirst()
                .map(rating -> "Stored ratings for " + rating.hotelName() + ": overall "
                        + value(rating.overallScore()) + "/10 from " + value(rating.reviewCount())
                        + " ratings; star rating " + value(rating.starRating()) + "; category ratings: "
                        + value(rating.categoryRatings()))
                .orElse("No rating details are stored for that hotel.");
    }

    @Transactional(readOnly = true)
    public String getMeals(String requestedHotelId) {
        String hotelId = validatedExistingHotelId(requestedHotelId);
        if (hotelId == null) return "Choose one hotel from find_hotels before requesting dining details.";

        List<HotelMealDetail> meals = repository.findMealDetails(hotelId);
        if (meals.isEmpty()) return "No dining or meal details are stored for that hotel.";

        StringBuilder result = new StringBuilder("Dining and meal information stored for hotel id ")
                .append(hotelId).append(" (up to 8 entries):\n");
        for (HotelMealDetail meal : meals) result.append("- ").append(meal.details()).append('\n');
        return result.toString().trim();
    }

    private String validatedExistingHotelId(String requestedHotelId) {
        if (requestedHotelId == null || requestedHotelId.isBlank()) return null;
        String hotelId = requestedHotelId.trim();
        if (!HOTEL_ID_PATTERN.matcher(hotelId).matches() || "all".equalsIgnoreCase(hotelId)) return null;
        return repository.hotelExists(hotelId) ? hotelId : null;
    }

    private static String window(String from, String until) {
        if (!present(from) && !present(until)) return "not recorded";
        if (!present(from)) return "until " + until;
        if (!present(until)) return "from " + from;
        return from + "–" + until;
    }

    private static String coordinates(String latitude, String longitude) {
        return present(latitude) && present(longitude) ? " (coordinates " + latitude + ", " + longitude + ")" : "";
    }

    private static String joined(String... values) {
        return java.util.Arrays.stream(values).filter(HotelProfileService::present)
                .map(String::trim).reduce((left, right) -> left + ", " + right).orElse("not recorded");
    }

    private static String value(String input) {
        return present(input) ? input.trim() : "not recorded";
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
