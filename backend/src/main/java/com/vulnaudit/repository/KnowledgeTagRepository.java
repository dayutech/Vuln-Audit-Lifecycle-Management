package com.vulnaudit.repository;

import com.vulnaudit.entity.KnowledgeTag;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 知识标签Repository接口
 */
@Repository
public interface KnowledgeTagRepository extends JpaRepository<KnowledgeTag, UUID> {

    List<KnowledgeTag> findByArticleId(@Nullable UUID articleId);
    List<KnowledgeTag> findByTag(@Nullable String tag);
    void deleteByArticleId(@Nullable UUID articleId);
    void deleteByArticleIdAndTag(@Nullable UUID articleId, @Nullable String tag);

}
