package com.tranquility.dashboard.persistence.repository;

import com.tranquility.dashboard.persistence.entity.HotelSyncPendingBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HotelSyncPendingBatchRepository extends JpaRepository<HotelSyncPendingBatch, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select batch from HotelSyncPendingBatch batch where batch.hotelId = :hotelId and batch.batchId = :batchId")
    Optional<HotelSyncPendingBatch> findByHotelAndBatchForUpdate(
            @Param("hotelId") String hotelId,
            @Param("batchId") UUID batchId
    );

    Optional<HotelSyncPendingBatch> findByHotelId(String hotelId);

    void deleteByHotelId(String hotelId);
}
