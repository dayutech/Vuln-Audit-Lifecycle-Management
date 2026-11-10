package com.vulnaudit.repository;

import com.vulnaudit.entity.DocumentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 知识库 / 文档附件仓储（t162） */
@Repository
public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachment, UUID> {

    List<DocumentAttachment> findByOwnerTypeAndOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(
            String ownerType, UUID ownerId);

    Optional<DocumentAttachment> findByIdAndOwnerTypeAndOwnerIdAndDeletedAtIsNull(
            UUID id, String ownerType, UUID ownerId);
}
