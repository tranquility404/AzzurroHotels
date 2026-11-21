package com.tranquility.shared.file;

import com.tranquility.shared.file.model.StoredFileDto;
import com.tranquility.shared.storage.model.UploadResult;

import java.util.Optional;
import java.util.UUID;

public interface StoredFilePort {

    StoredFileDto savePrivate(String ownerId, UploadResult result);

    StoredFileDto savePublic(
            String ownerId,
            UploadResult result,
            String publicUrl
    );

    Optional<StoredFileDto> findById(UUID fileId);

    Optional<StoredFileDto> findByIdAndOwnerId(UUID fileId, String ownerId);

    void delete(UUID fileId);
}
