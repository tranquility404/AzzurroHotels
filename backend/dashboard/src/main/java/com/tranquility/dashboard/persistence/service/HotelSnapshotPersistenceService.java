package com.tranquility.dashboard.persistence.service;

import com.tranquility.dashboard.persistence.entity.Hotel;
import com.tranquility.dashboard.mapper.HotelSnapshotMapper;
import com.tranquility.dashboard.model.HotelJsonModel;
import com.tranquility.dashboard.persistence.repository.HotelRepository;
import com.tranquility.shared.file.StoredFilePort;
import com.tranquility.shared.storage.model.UploadResult;
import com.tranquility.shared.storage.model.StorageLocation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class HotelSnapshotPersistenceService {

    private static final String SNAPSHOT_METADATA_OWNER = "system:hotel-catalog";

    private final HotelRepository hotelRepository;
    private final HotelSnapshotMapper snapshotMapper;
    private final StoredFilePort storedFilePort;

    public HotelSnapshotPersistenceService(
            HotelRepository hotelRepository,
            HotelSnapshotMapper snapshotMapper,
            StoredFilePort storedFilePort
    ) {
        this.hotelRepository = hotelRepository;
        this.snapshotMapper = snapshotMapper;
        this.storedFilePort = storedFilePort;
    }

    @Transactional
    public void persist(JsonNode snapshotJson, UploadResult uploadResult) {
        HotelJsonModel.Snapshot snapshot = snapshotMapper.parse(snapshotJson);
        if (snapshot.hotelId() == null || snapshot.hotelId().isBlank()) {
            throw new IllegalArgumentException("The hotel snapshot is missing its hotel id.");
        }
        if (snapshot.hotelDetails() == null || snapshot.collectedAt() == null) {
            throw new IllegalArgumentException("The hotel snapshot is missing its details or collection date.");
        }

        storedFilePort.savePrivate(SNAPSHOT_METADATA_OWNER, uploadResult);

        StorageLocation storageLocation = uploadResult.storageLocation();
        Hotel hotel = hotelRepository.findById(snapshot.hotelId()).orElse(null);
        if (hotel == null) {
            hotel = Hotel.fromSnapshot(snapshot);
        }
        hotel.replaceWith(snapshot);
        hotel.setSnapshotLocation(storageLocation);
        hotelRepository.saveAndFlush(hotel);
    }
}
