package com.tranquility.dashboard.service;

import com.tranquility.dashboard.mapper.HotelSnapshotMapper;
import com.tranquility.dashboard.model.HotelJsonModel;
import com.tranquility.dashboard.persistence.service.HotelSnapshotPersistenceService;
import com.tranquility.shared.storage.ObjectStorage;
import com.tranquility.shared.storage.model.StorageBucket;
import com.tranquility.shared.storage.model.StorageLocation;
import com.tranquility.shared.storage.model.UploadObject;
import com.tranquility.shared.storage.model.UploadResult;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Service
public class HotelSnapshotArchiveService {

    private static final String SNAPSHOT_PATH = "hotels";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final ObjectStorage objectStorage;
    private final HotelSnapshotPersistenceService persistenceService;
    private final HotelSnapshotMapper snapshotMapper;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public HotelSnapshotArchiveService(
            ObjectStorage objectStorage,
            HotelSnapshotPersistenceService persistenceService,
            HotelSnapshotMapper snapshotMapper
    ) {
        this.objectStorage = objectStorage;
        this.persistenceService = persistenceService;
        this.snapshotMapper = snapshotMapper;
    }

    /** Uploads the JSON object first, then persists its relational/JSONB representation. */
    public StorageLocation archive(String fileName, JsonNode snapshot) {
        HotelJsonModel.Snapshot parsedSnapshot = snapshotMapper.parse(snapshot);
        String expectedFileName = safeHotelFileName(parsedSnapshot.hotelId());
        if (!expectedFileName.equals(fileName)) {
            throw new IllegalArgumentException("The snapshot filename does not match its hotel id.");
        }

        UploadResult result = upload(snapshot, fileName, fileName);
        persistenceService.persist(snapshot, result);
        return result.storageLocation();
    }

    /** Uploads a confirmed sync candidate to an immutable, batch-specific object key. */
    public UploadResult uploadVersionedSnapshot(JsonNode snapshot, UUID batchId) {
        HotelJsonModel.Snapshot parsedSnapshot = snapshotMapper.parse(snapshot);
        String fileName = safeHotelFileName(parsedSnapshot.hotelId());
        String safeId = fileName.substring(0, fileName.length() - ".json".length());
        String objectId = safeId + "-" + batchId + ".json";
        return upload(snapshot, objectId, fileName);
    }

    private UploadResult upload(JsonNode snapshot, String objectId, String originalFileName) {
        byte[] content;
        try {
            content = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalArgumentException("The hotel snapshot could not be serialized.", exception);
        }

        UploadObject upload = new UploadObject(
                StorageBucket.APP_BUCKET,
                SNAPSHOT_PATH,
                objectId,
                content,
                originalFileName,
                JSON_CONTENT_TYPE,
                content.length);
        return objectStorage.upload(upload);
    }

    public static String safeHotelFileName(String hotelId) {
        if (hotelId == null || hotelId.isBlank()) {
            throw new IllegalArgumentException("The hotel snapshot is missing its hotel id.");
        }
        String safeId = hotelId.replaceAll("[^A-Za-z0-9_-]", "_");
        if (safeId.isBlank()) {
            throw new IllegalArgumentException("The hotel id cannot be used as a file name.");
        }
        return safeId + ".json";
    }
}
