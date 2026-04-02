package com.vulnaudit.repository;

import com.vulnaudit.entity.Document;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    List<Document> findByAssetId(@Nullable UUID assetId);
    List<Document> findByAuthorId(@Nullable UUID authorId);
    List<Document> findByStatus(@Nullable String status);
    List<Document> findByCategory(@Nullable String category);
    List<Document> findByCreatedBy(@Nullable UUID createdBy);
    boolean existsByTitle(@Nullable String title);

    @org.springframework.data.jpa.repository.Query("SELECT d FROM Document d WHERE d.title LIKE %:keyword% OR d.content LIKE %:keyword%")
    List<Document> search(@org.springframework.data.repository.query.Param("keyword") String keyword);

}
