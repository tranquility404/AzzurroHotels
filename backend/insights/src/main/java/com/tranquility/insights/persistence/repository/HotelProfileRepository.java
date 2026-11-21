package com.tranquility.insights.persistence.repository;

import com.tranquility.insights.model.hotels.*;
import com.tranquility.insights.model.hotels.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class HotelProfileRepository {

    private static final int MAX_HOTEL_CHOICES = 10;
    private static final int MAX_NEARBY_PLACES = 20;
    private static final int MAX_FACILITIES = 30;
    private static final int MAX_ROOM_OPTIONS = 12;
    private static final int MAX_MEAL_DETAILS = 8;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public HotelProfileRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<HotelCandidate> searchHotels(String searchTerm) {
        String normalized = searchTerm == null ? "" : searchTerm.trim();
        String pattern = normalized.isEmpty() ? null : "%" + normalized + "%";
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("searchTerm", normalized)
                .addValue("searchPattern", pattern)
                .addValue("resultLimit", MAX_HOTEL_CHOICES);

        return jdbcTemplate.query("""
                select hotel.hotel_id as hotel_id,
                       left(hotel.hotel_name, 160) as hotel_name,
                       coalesce(nullif(left(concat_ws(', ',
                           nullif(hotel.hotel_details #>> '{location,city}', ''),
                           nullif(hotel.hotel_details #>> '{location,region}', ''),
                           nullif(hotel.hotel_details #>> '{location,country}', '')), 220), ''), 'Location unavailable') as location,
                       coalesce(nullif(left(hotel.hotel_details #>> '{rating,score}', 24), ''), 'not recorded') as overall_rating,
                       coalesce(nullif(left(hotel.hotel_details #>> '{rating,count}', 12), ''), 'not recorded') as rating_count,
                       review_counts.review_count as review_count,
                       coalesce(nearby.matching_places, '') as nearby_matches
                from hotels hotel
                left join lateral (
                    select count(*) as review_count
                    from reviews review
                    where review.hotel_id = hotel.hotel_id
                ) review_counts on true
                left join lateral (
                    select string_agg(
                                 concat_ws(' ', nullif(left(matched.place ->> 'name', 120), ''),
                                     case when nullif(matched.place ->> 'distance', '') is null then null
                                          else concat('(', left(matched.place ->> 'distance', 60), ')') end),
                               '; ' order by matched.category, matched.place ->> 'name') as matching_places
                    from (
                        select category.key as category, place.value as place
                        from jsonb_each(case
                            when jsonb_typeof(hotel.hotel_details -> 'surroundings') = 'object'
                            then hotel.hotel_details -> 'surroundings'
                            else '{}'::jsonb
                        end) as category(key, value)
                        cross join lateral jsonb_array_elements(case
                            when jsonb_typeof(category.value) = 'array' then category.value
                            else '[]'::jsonb
                        end) as place(value)
                        where cast(:searchPattern as text) is not null
                          and place.value::text ilike :searchPattern
                        limit 5
                    ) matched
                ) nearby on true
                where cast(:searchPattern as text) is null
                   or hotel.hotel_id = :searchTerm
                   or hotel.hotel_name ilike :searchPattern
                   or hotel.hotel_details::text ilike :searchPattern
                order by case
                           when hotel.hotel_id = :searchTerm then 0
                           when lower(hotel.hotel_name) = lower(:searchTerm) then 1
                           else 2
                         end,
                          review_counts.review_count desc,
                         hotel.hotel_name asc
                limit :resultLimit
                """, parameters, (resultSet, rowNumber) -> new HotelCandidate(
                resultSet.getString("hotel_id"),
                resultSet.getString("hotel_name"),
                resultSet.getString("location"),
                resultSet.getString("overall_rating"),
                resultSet.getString("rating_count"),
                resultSet.getLong("review_count"),
                resultSet.getString("nearby_matches")));
    }

    public List<HotelLocation> findLocation(String hotelId) {
        return jdbcTemplate.query("""
                select hotel_id,
                       hotel_name,
                       left(hotel_details #>> '{location,address}', 240) as address,
                       left(hotel_details #>> '{location,city}', 100) as city,
                       left(hotel_details #>> '{location,region}', 100) as region,
                       left(hotel_details #>> '{location,country}', 100) as country,
                       left(hotel_details #>> '{location,postal_code}', 32) as postal_code,
                       left(hotel_details #>> '{location,latitude}', 32) as latitude,
                       left(hotel_details #>> '{location,longitude}', 32) as longitude
                from hotels
                where hotel_id = :hotelId
                """, hotelParameters(hotelId), (resultSet, rowNumber) -> new HotelLocation(
                resultSet.getString("hotel_id"),
                resultSet.getString("hotel_name"),
                resultSet.getString("address"),
                resultSet.getString("city"),
                resultSet.getString("region"),
                resultSet.getString("country"),
                resultSet.getString("postal_code"),
                resultSet.getString("latitude"),
                resultSet.getString("longitude")));
    }

    /** Returns only compact geographic fields, not each hotel's full profile. */
    public List<HotelLocation> findHotelLocationInventory() {
        return jdbcTemplate.query("""
                select hotel_id,
                       left(hotel_name, 160) as hotel_name,
                       left(hotel_details #>> '{location,address}', 240) as address,
                       left(hotel_details #>> '{location,city}', 100) as city,
                       left(hotel_details #>> '{location,region}', 100) as region,
                       left(hotel_details #>> '{location,country}', 100) as country,
                       left(hotel_details #>> '{location,postal_code}', 32) as postal_code,
                       left(hotel_details #>> '{location,latitude}', 32) as latitude,
                       left(hotel_details #>> '{location,longitude}', 32) as longitude
                from hotels
                order by hotel_name, hotel_id
                """, new MapSqlParameterSource(),
                (resultSet, rowNumber) -> new HotelLocation(
                        resultSet.getString("hotel_id"),
                        resultSet.getString("hotel_name"),
                        resultSet.getString("address"),
                        resultSet.getString("city"),
                        resultSet.getString("region"),
                        resultSet.getString("country"),
                        resultSet.getString("postal_code"),
                        resultSet.getString("latitude"),
                        resultSet.getString("longitude")));
    }

    public List<HotelNearbyPlace> findNearbyPlaces(String hotelId, String searchTerm) {
        String normalized = searchTerm == null ? "" : searchTerm.trim();
        MapSqlParameterSource parameters = hotelParameters(hotelId)
                .addValue("searchPattern", normalized.isEmpty() ? null : "%" + normalized + "%")
                .addValue("resultLimit", MAX_NEARBY_PLACES);
        return jdbcTemplate.query(SURROUNDINGS_SQL, parameters, (resultSet, rowNumber) -> new HotelNearbyPlace(
                resultSet.getString("category"),
                resultSet.getString("name"),
                resultSet.getString("type"),
                resultSet.getString("distance"),
                resultSet.getString("walk_time"),
                resultSet.getString("drive_time")));
    }

    public List<HotelFacility> findFacilities(String hotelId) {
        return jdbcTemplate.query("""
                select left(facility.value ->> 'name', 120) as name,
                       left(facility.value ->> 'slug', 80) as slug
                from hotels hotel
                cross join lateral jsonb_array_elements(case
                    when jsonb_typeof(hotel.hotel_details -> 'facilities') = 'array'
                    then hotel.hotel_details -> 'facilities'
                    else '[]'::jsonb
                end) as facility(value)
                where hotel.hotel_id = :hotelId
                order by facility.value ->> 'name'
                limit :resultLimit
                """, hotelParameters(hotelId).addValue("resultLimit", MAX_FACILITIES),
                (resultSet, rowNumber) -> new HotelFacility(
                        resultSet.getString("name"), resultSet.getString("slug")));
    }

    public List<HotelRoomOption> findRoomOptions(String hotelId) {
        return jdbcTemplate.query("""
                select left(room.value ->> 'name', 120) as name,
                       case when length(room.value ->> 'description') > 320
                            then left(room.value ->> 'description', 320) || '… [truncated]'
                            else room.value ->> 'description' end as description,
                       left(room.value ->> 'size_m2', 32) as size_square_meters,
                       left(room.value ->> 'max_occupancy', 16) as max_occupancy,
                       left(room.value ->> 'max_children', 16) as max_children,
                       coalesce((
                           select string_agg(
                               concat(coalesce(left(bed.value ->> 'count', 8), '1'), ' ',
                                   coalesce(left(bed.value ->> 'type', 40), 'bed')),
                               ', ' order by bed.ordinality)
                           from jsonb_array_elements(case
                               when jsonb_typeof(room.value -> 'beds') = 'array' then room.value -> 'beds'
                               else '[]'::jsonb
                            end) with ordinality as bed(value, ordinality)
                           where bed.ordinality <= 8
                       ), '') as beds,
                       case when length((room.value -> 'facility_highlights')::text) > 240
                            then left((room.value -> 'facility_highlights')::text, 240) || '… [truncated]'
                            else coalesce((room.value -> 'facility_highlights')::text, '') end as facility_highlights
                from hotels hotel
                cross join lateral jsonb_array_elements(case
                    when jsonb_typeof(hotel.hotel_details -> 'rooms') = 'array'
                    then hotel.hotel_details -> 'rooms'
                    else '[]'::jsonb
                end) with ordinality as room(value, ordinality)
                where hotel.hotel_id = :hotelId
                order by room.ordinality
                limit :resultLimit
                """, hotelParameters(hotelId).addValue("resultLimit", MAX_ROOM_OPTIONS),
                (resultSet, rowNumber) -> new HotelRoomOption(
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getString("size_square_meters"),
                        resultSet.getString("max_occupancy"),
                        resultSet.getString("max_children"),
                        resultSet.getString("beds"),
                        resultSet.getString("facility_highlights")));
    }

    public List<HotelPolicySummary> findPolicies(String hotelId) {
        return jdbcTemplate.query("""
                select hotel_name,
                       hotel_details #>> '{policies,checkin,from}' as checkin_from,
                       hotel_details #>> '{policies,checkin,until}' as checkin_until,
                       hotel_details #>> '{policies,checkout,from}' as checkout_from,
                       hotel_details #>> '{policies,checkout,until}' as checkout_until,
                       hotel_details #>> '{policies,pets}' as pets,
                       hotel_details #>> '{policies,allows_children}' as allows_children,
                       hotel_details #>> '{policies,min_child_age}' as minimum_child_age,
                       hotel_details #>> '{policies,has_cribs}' as cribs_available,
                       hotel_details #>> '{policies,has_extra_beds}' as extra_beds_available,
                       coalesce((
                           select string_agg(left(payment.value, 100), ', ' order by payment.ordinality)
                           from (
                               select payment.value, payment.ordinality
                               from jsonb_array_elements_text(case
                                   when jsonb_typeof(hotel.hotel_details #> '{policies,accepted_payments}') = 'array'
                                   then hotel.hotel_details #> '{policies,accepted_payments}'
                                   else '[]'::jsonb
                               end) with ordinality as payment(value, ordinality)
                               order by payment.ordinality
                               limit 10
                           ) as payment
                       ), '') as accepted_payments,
                        case when length(hotel.hotel_details ->> 'fine_print') > 1500
                             then left(hotel.hotel_details ->> 'fine_print', 1500) || '… [truncated]'
                             else hotel.hotel_details ->> 'fine_print' end as fine_print
                from hotels hotel
                where hotel.hotel_id = :hotelId
                """, hotelParameters(hotelId), (resultSet, rowNumber) -> new HotelPolicySummary(
                resultSet.getString("hotel_name"),
                resultSet.getString("checkin_from"),
                resultSet.getString("checkin_until"),
                resultSet.getString("checkout_from"),
                resultSet.getString("checkout_until"),
                resultSet.getString("pets"),
                resultSet.getString("allows_children"),
                resultSet.getString("minimum_child_age"),
                resultSet.getString("cribs_available"),
                resultSet.getString("extra_beds_available"),
                resultSet.getString("accepted_payments"),
                resultSet.getString("fine_print")));
    }

    public List<HotelRatingSummary> findRatings(String hotelId) {
        return jdbcTemplate.query("""
                select hotel_name,
                       left(hotel_details #>> '{rating,score}', 24) as overall_score,
                       left(hotel_details #>> '{rating,count}', 12) as review_count,
                       left(hotel_details #>> '{rating,stars}', 12) as star_rating,
                       coalesce((
                           select string_agg(
                                concat_ws(': ', nullif(left(category.value ->> 'name', 80), ''),
                                    nullif(left(category.value ->> 'score', 24), ''))
                                     || case when nullif(category.value ->> 'count', '') is null then ''
                                             else concat(' (', left(category.value ->> 'count', 12), ' ratings)') end,
                               '; ' order by category.ordinality)
                           from jsonb_array_elements(case
                               when jsonb_typeof(hotel.hotel_details #> '{rating,subscores}') = 'array'
                               then hotel.hotel_details #> '{rating,subscores}'
                               else '[]'::jsonb
                           end) with ordinality as category(value, ordinality)
                           where category.ordinality <= 20
                       ), '') as category_ratings
                from hotels hotel
                where hotel.hotel_id = :hotelId
                """, hotelParameters(hotelId), (resultSet, rowNumber) -> new HotelRatingSummary(
                resultSet.getString("hotel_name"),
                resultSet.getString("overall_score"),
                resultSet.getString("review_count"),
                resultSet.getString("star_rating"),
                resultSet.getString("category_ratings")));
    }

    public List<HotelMealDetail> findMealDetails(String hotelId) {
        return jdbcTemplate.query("""
                select case when length(meal.value::text) > 300
                            then left(meal.value::text, 300) || '… [truncated]'
                            else meal.value::text end as details
                from hotels hotel
                cross join lateral jsonb_array_elements(case
                    when jsonb_typeof(hotel.hotel_details -> 'meals') = 'array'
                    then hotel.hotel_details -> 'meals'
                    else '[]'::jsonb
                end) with ordinality as meal(value, ordinality)
                where hotel.hotel_id = :hotelId
                order by meal.ordinality
                limit :resultLimit
                """, hotelParameters(hotelId).addValue("resultLimit", MAX_MEAL_DETAILS),
                (resultSet, rowNumber) -> new HotelMealDetail(resultSet.getString("details")));
    }

    public boolean hotelExists(String hotelId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from hotels where hotel_id = :hotelId)",
                hotelParameters(hotelId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private static MapSqlParameterSource hotelParameters(String hotelId) {
        return new MapSqlParameterSource("hotelId", hotelId);
    }

    private static final String SURROUNDINGS_SQL = """
            with nearby_places(category, name, type, distance, walk_time, drive_time) as (
                select 'Landmarks', item.value ->> 'name', item.value ->> 'type', item.value ->> 'distance',
                       item.value ->> 'walk_time', item.value ->> 'drive_time'
                from hotels hotel
                cross join lateral jsonb_array_elements(case
                    when jsonb_typeof(hotel.hotel_details #> '{surroundings,landmarks}') = 'array'
                    then hotel.hotel_details #> '{surroundings,landmarks}' else '[]'::jsonb end) as item(value)
                where hotel.hotel_id = :hotelId
                union all
                select 'Restaurants', item.value ->> 'name', item.value ->> 'type', item.value ->> 'distance',
                       item.value ->> 'walk_time', item.value ->> 'drive_time'
                from hotels hotel
                cross join lateral jsonb_array_elements(case
                    when jsonb_typeof(hotel.hotel_details #> '{surroundings,restaurants}') = 'array'
                    then hotel.hotel_details #> '{surroundings,restaurants}' else '[]'::jsonb end) as item(value)
                where hotel.hotel_id = :hotelId
                union all
                select 'Public transport', item.value ->> 'name', item.value ->> 'type', item.value ->> 'distance',
                       item.value ->> 'walk_time', item.value ->> 'drive_time'
                from hotels hotel
                cross join lateral jsonb_array_elements(case
                    when jsonb_typeof(hotel.hotel_details #> '{surroundings,public_transport}') = 'array'
                    then hotel.hotel_details #> '{surroundings,public_transport}' else '[]'::jsonb end) as item(value)
                where hotel.hotel_id = :hotelId
                union all
                select 'Airports', item.value ->> 'name', item.value ->> 'type', item.value ->> 'distance',
                       item.value ->> 'walk_time', item.value ->> 'drive_time'
                from hotels hotel
                cross join lateral jsonb_array_elements(case
                    when jsonb_typeof(hotel.hotel_details #> '{surroundings,airports}') = 'array'
                    then hotel.hotel_details #> '{surroundings,airports}' else '[]'::jsonb end) as item(value)
                where hotel.hotel_id = :hotelId
                union all
                select 'Beaches', item.value ->> 'name', item.value ->> 'type', item.value ->> 'distance',
                       item.value ->> 'walk_time', item.value ->> 'drive_time'
                from hotels hotel
                cross join lateral jsonb_array_elements(case
                    when jsonb_typeof(hotel.hotel_details #> '{surroundings,beaches}') = 'array'
                    then hotel.hotel_details #> '{surroundings,beaches}' else '[]'::jsonb end) as item(value)
                where hotel.hotel_id = :hotelId
                union all
                select 'Natural beauty', item.value ->> 'name', item.value ->> 'type', item.value ->> 'distance',
                       item.value ->> 'walk_time', item.value ->> 'drive_time'
                from hotels hotel
                cross join lateral jsonb_array_elements(case
                    when jsonb_typeof(hotel.hotel_details #> '{surroundings,natural_beauty}') = 'array'
                    then hotel.hotel_details #> '{surroundings,natural_beauty}' else '[]'::jsonb end) as item(value)
                where hotel.hotel_id = :hotelId
            )
            select left(category, 80) as category,
                   left(name, 160) as name,
                   left(type, 100) as type,
                   left(distance, 60) as distance,
                   left(walk_time, 60) as walk_time,
                   left(drive_time, 60) as drive_time
            from nearby_places
            where cast(:searchPattern as text) is null
               or concat_ws(' ', name, type) ilike :searchPattern
            order by category, name
            limit :resultLimit
            """;
}
