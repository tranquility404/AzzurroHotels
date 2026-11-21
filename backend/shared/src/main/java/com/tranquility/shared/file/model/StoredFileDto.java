package com.tranquility.shared.file.model;

import com.tranquility.shared.storage.model.StorageBucket;

import java.util.UUID;

public record StoredFileDto(
        UUID id,
        String ownerId,
        StorageBucket bucket,
        String path,
        String fileId,
        String originalFileName,
        String contentType,
        long size,
        FileVisibility visibility,
        String publicUrl
) {}