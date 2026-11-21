package com.tranquility.insights.tools;

import com.tranquility.insights.persistence.service.HotelProfileService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class HotelProfileTools {

    private final HotelProfileService hotelProfileService;

    public HotelProfileTools(HotelProfileService hotelProfileService) {
        this.hotelProfileService = hotelProfileService;
    }

    @Tool(name = "find_hotels", description = "Find and disambiguate hotels by name or id. Returns at most 10 compact choices with basic location, overall rating, and stored review count. For broad hotel-profile questions, use an empty search and ask which hotel. For geographic proximity questions such as whether any property is near a river, use get_hotel_location_inventory instead of searching for the geographic feature.")
    public String findHotels(
            @ToolParam(description = "Hotel name/id or location/nearby-place search. Use empty only to show compact choices and ask which hotel.") String searchTerm
    ) {
        return hotelProfileService.findHotels(searchTerm);
    }

    @Tool(name = "get_hotel_location_inventory", description = "Return compact address/city/region/country/coordinate fields for every recorded hotel. Use this when the guest asks whether any property is near a geographic feature or place not necessarily named in hotel records. Do not return facilities or other profile details. The assistant may use general geographic knowledge to identify likely nearby properties, but must not invent a precise distance not present in data.")
    public String getHotelLocationInventory() {
        return hotelProfileService.getHotelLocationInventory();
    }

    @Tool(name = "get_hotel_location", description = "Return the address and stored location fields for one selected hotel only.")
    public String getHotelLocation(
            @ToolParam(description = "One exact hotel id selected from find_hotels.") String hotelId
    ) {
        return hotelProfileService.getLocation(hotelId);
    }

    @Tool(name = "get_hotel_nearby_places", description = "Return nearby landmarks, restaurants, public transport, airports, beaches, and natural places for one selected hotel. Results are capped at 20.")
    public String getHotelNearbyPlaces(
            @ToolParam(description = "One exact hotel id selected from find_hotels.") String hotelId,
            @ToolParam(description = "Optional place/category clue to narrow results, e.g. station or restaurant; empty returns up to 20 stored entries.") String searchTerm
    ) {
        return hotelProfileService.getNearbyPlaces(hotelId, searchTerm);
    }

    @Tool(name = "get_hotel_facilities", description = "Return up to 30 facility names recorded for one selected hotel.")
    public String getHotelFacilities(
            @ToolParam(description = "One exact hotel id selected from find_hotels.") String hotelId
    ) {
        return hotelProfileService.getFacilities(hotelId);
    }

    @Tool(name = "get_hotel_rooms", description = "Return up to 12 stored room options, occupancy, beds, and descriptions for one selected hotel. Stored room data is not live availability.")
    public String getHotelRooms(
            @ToolParam(description = "One exact hotel id selected from find_hotels.") String hotelId
    ) {
        return hotelProfileService.getRooms(hotelId);
    }

    @Tool(name = "get_hotel_policies", description = "Return stored check-in/out windows, pet/children rules, crib/extra-bed details, payment methods, and bounded fine print for one selected hotel.")
    public String getHotelPolicies(
            @ToolParam(description = "One exact hotel id selected from find_hotels.") String hotelId
    ) {
        return hotelProfileService.getPolicies(hotelId);
    }

    @Tool(name = "get_hotel_ratings", description = "Return the stored overall rating and category rating subscores for one selected hotel.")
    public String getHotelRatings(
            @ToolParam(description = "One exact hotel id selected from find_hotels.") String hotelId
    ) {
        return hotelProfileService.getRatings(hotelId);
    }

    @Tool(name = "get_hotel_meals", description = "Return up to eight dining/meal entries stored for one selected hotel. Only report meal hours if the stored entry contains them.")
    public String getHotelMeals(
            @ToolParam(description = "One exact hotel id selected from find_hotels.") String hotelId
    ) {
        return hotelProfileService.getMeals(hotelId);
    }
}
