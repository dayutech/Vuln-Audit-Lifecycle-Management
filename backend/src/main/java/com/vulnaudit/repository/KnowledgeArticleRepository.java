package com.vulnaudit.repository;

import com.vulnaudit.entity.KnowledgeArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, UUID>, JpaSpecificationExecutor<KnowledgeArticle> {

    List<KnowledgeArticle> findByCategory(String category);
    List<KnowledgeArticle> findByAuthorId(UUID authorId);
    List<KnowledgeArticle> findByStatus(String status);
    
    @Query("SELECT k FROM KnowledgeArticle k WHERE LOWER(k.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(k.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<KnowledgeArticle> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT k FROM KnowledgeArticle k WHERE LOWER(k.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(k.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<KnowledgeArticle> search(@Param("keyword") String keyword);

}
