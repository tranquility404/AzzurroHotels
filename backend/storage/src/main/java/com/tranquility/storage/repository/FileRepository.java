package com.tranquility.storage.repository;

import com.tranquility.storage.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<StoredFile, UUID> {
    Optional<StoredFile> findByFileId(String fileId);

    Optional<StoredFile> findByIdAndOwnerId(UUID id, String ownerId);

    Page<StoredFile> findByOwnerId(String ownerId, Pageable pageable);
}
