package com.vulnaudit.repository;

import com.vulnaudit.entity.KnowledgeTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 知识标签Repository接口
 */
@Repository
public interface KnowledgeTagRepository extends JpaRepository<KnowledgeTag, UUID> {

    List<KnowledgeTag> findByArticleId(UUID articleId);
    List<KnowledgeTag> findByTag(String tag);
    void deleteByArticleId(UUID articleId);
    void deleteByArticleIdAndTag(UUID articleId, String tag);

}
