package com.tranquility.storage.adapter.persistence;

import com.tranquility.shared.file.StoredFilePort;
import com.tranquility.shared.file.model.FileVisibility;
import com.tranquility.shared.file.model.StoredFileDto;
import com.tranquility.shared.storage.model.UploadResult;
import com.tranquility.storage.entity.StoredFile;
import com.tranquility.storage.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaStoredFileAdapter implements StoredFilePort {

    private final FileRepository fileRepository;

    @Override
    @Transactional
    public StoredFileDto savePrivate(String ownerId, UploadResult result) {
        return save(ownerId, result, FileVisibility.PRIVATE, null);
    }

    @Override
    @Transactional
    public StoredFileDto savePublic(String ownerId, UploadResult result, String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new IllegalArgumentException("A public URL is required for public file metadata.");
        }
        return save(ownerId, result, FileVisibility.PUBLIC, publicUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredFileDto> findById(UUID fileId) {
        return fileRepository.findById(fileId).map(JpaStoredFileAdapter::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredFileDto> findByIdAndOwnerId(UUID fileId, String ownerId) {
        return fileRepository.findByIdAndOwnerId(fileId, ownerId).map(JpaStoredFileAdapter::toDto);
    }

    @Override
    @Transactional
    public void delete(UUID fileId) {
        fileRepository.deleteById(fileId);
    }

    private StoredFileDto save(String ownerId, UploadResult result, FileVisibility visibility, String publicUrl) {
        validate(ownerId, result);

        StoredFile file = fileRepository.findByFileId(result.objectId())
                .map(existing -> updateExisting(existing, ownerId, result, visibility, publicUrl))
                .orElseGet(() -> visibility == FileVisibility.PRIVATE
                        ? StoredFile.createPrivateFile(ownerId, result)
                        : StoredFile.createPublicFile(ownerId, result, publicUrl));

        return toDto(fileRepository.save(file));
    }

    private StoredFile updateExisting(
            StoredFile existing,
            String ownerId,
            UploadResult result,
            FileVisibility visibility,
            String publicUrl
    ) {
        if (!existing.getOwnerId().equals(ownerId)
                || existing.getBucket() != result.bucket()
                || !existing.getPath().equals(result.path())
                || existing.getVisibility() != visibility) {
            throw new IllegalStateException(
                    "The storage object id is already registered with different owner or location metadata.");
        }

        existing.setOriginalFileName(result.originalFileName());
        existing.setContentType(result.contentType());
        existing.setSize(result.size());
        existing.setPublicUrl(publicUrl);
        return existing;
    }

    private void validate(String ownerId, UploadResult result) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("A metadata owner id is required.");
        }
        Objects.requireNonNull(result, "An upload result is required.");
        if (result.bucket() == null || result.path() == null || result.path().isBlank()
                || result.objectId() == null || result.objectId().isBlank()
                || result.originalFileName() == null || result.originalFileName().isBlank()
                || result.contentType() == null || result.contentType().isBlank() || result.size() < 0) {
            throw new IllegalArgumentException("The upload result is missing required file metadata.");
        }
    }

    private static StoredFileDto toDto(StoredFile file) {
        return new StoredFileDto(
                file.getId(),
                file.getOwnerId(),
                file.getBucket(),
                file.getPath(),
                file.getFileId(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getSize(),
                file.getVisibility(),
                file.getPublicUrl());
    }
}
