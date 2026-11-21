package com.tranquility.shared.storage.model;

public record StorageLocation(
        StorageBucket bucket,
        String path,
        String objectId
) {
    public String objectKey() {
        return "%s/%s".formatted(path, objectId);
    }
}