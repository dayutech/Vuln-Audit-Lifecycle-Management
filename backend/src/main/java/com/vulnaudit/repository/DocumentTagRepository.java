package com.vulnaudit.repository;

import com.vulnaudit.entity.DocumentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentTagRepository extends JpaRepository<DocumentTag, UUID> {

    List<DocumentTag> findByDocumentId(UUID documentId);
    
    @Query("SELECT dt.tag FROM DocumentTag dt WHERE dt.documentId = :documentId")
    List<String> findTagsByDocumentId(@Param("documentId") UUID documentId);

}
