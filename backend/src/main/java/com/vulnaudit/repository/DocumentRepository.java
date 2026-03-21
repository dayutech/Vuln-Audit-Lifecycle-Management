package com.vulnaudit.repository;

import com.vulnaudit.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    List<Document> findByAssetId(UUID assetId);
    List<Document> findByAuthorId(UUID authorId);
    List<Document> findByStatus(String status);
    List<Document> findByCategory(String category);
    List<Document> findByCreatedBy(UUID createdBy);
    boolean existsByTitle(String title);
    
    @org.springframework.data.jpa.repository.Query("SELECT d FROM Document d WHERE d.title LIKE %:keyword% OR d.content LIKE %:keyword%")
    List<Document> search(@org.springframework.data.repository.query.Param("keyword") String keyword);

}
