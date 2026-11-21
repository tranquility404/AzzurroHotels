package com.tranquility.shared.storage;

import com.tranquility.shared.storage.model.StorageLocation;
import com.tranquility.shared.storage.model.UploadResult;
import com.tranquility.shared.storage.model.UploadObject;

import java.io.InputStream;
import java.time.Duration;

public interface ObjectStorage {

    UploadResult upload(UploadObject object);

    InputStream download(StorageLocation location);

    String generateAccessUrl(StorageLocation location, Duration expiresIn);

    String generateUploadUrl(StorageLocation location, String contentType, Duration expiresIn);

    String generatePublicUrl(StorageLocation location);

    void delete(StorageLocation location);
}
