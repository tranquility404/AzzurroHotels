package com.tranquility.shared.storage.model;

public record UploadResult(
        StorageBucket bucket,
        String path,
        String objectId,
        String originalFileName,
        String contentType,
        long size
) {
    public static UploadResult create(UploadObject object) {
        return new UploadResult(
                object.bucket(),
                object.path(),
                object.objectId(),
                object.originalFileName(),
                object.contentType(),
                object.size()
        );
    }

    public StorageLocation storageLocation() {
        return new StorageLocation(
                bucket,
                path,
                objectId
        );
    }
}