package com.tranquility.dashboard.persistence.repository;

import com.tranquility.dashboard.persistence.entity.Hotel;
import com.tranquility.dashboard.persistence.projection.DashboardPropertyProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HotelRepository extends JpaRepository<Hotel, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hotel from Hotel hotel where hotel.hotelId = :hotelId")
    Optional<Hotel> findByHotelIdForUpdate(@Param("hotelId") String hotelId);

    List<Hotel> findAllByOrderByCollectedAtDescHotelIdAsc();

    @Query(value = """
            select hotel.hotel_id as "hotelId",
                   hotel.hotel_name as "hotelName",
                   coalesce(nullif(hotel.hotel_details #>> '{location,city}', ''),
                            nullif(hotel.hotel_details #>> '{location,region}', ''),
                            nullif(hotel.hotel_details #>> '{location,country}', ''),
                            'Location unavailable') as "city",
                   coalesce(nullif(hotel.hotel_details #>> '{location,country}', ''),
                            'Location unavailable') as "country"
            from hotels hotel
            order by hotel.collected_at desc, hotel.hotel_id asc
            """, nativeQuery = true)
    List<DashboardPropertyProjection> findDashboardProperties();
}
