package com.tranquility.shared.storage.model;

import java.util.UUID;

public record UploadObject(
        StorageBucket bucket,
        String path,
        String objectId,
        byte[] content,
        String originalFileName,
        String contentType,
        long size
) {
    public UploadObject {
        if (objectId == null) {
            objectId = UUID.randomUUID().toString();
        }
    }

    public String objectKey() {
        return "%s/%s".formatted(path, objectId);
    }
}