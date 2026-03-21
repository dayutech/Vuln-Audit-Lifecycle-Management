package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.KnowledgeArticle;
import com.vulnaudit.entity.KnowledgeComment;
import com.vulnaudit.entity.KnowledgeTag;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.KnowledgeArticleRepository;
import com.vulnaudit.repository.KnowledgeCommentRepository;
import com.vulnaudit.repository.KnowledgeTagRepository;
import com.vulnaudit.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeCommentRepository commentRepository;
    private final KnowledgeTagRepository tagRepository;

    @Override
    @Transactional
    public KnowledgeArticle createArticle(KnowledgeArticle article) {
        log.info("创建知识文章: title={}", article.getTitle());
        return articleRepository.save(article);
    }

    @Override
    public Optional<KnowledgeArticle> findById(UUID id) {
        return articleRepository.findById(id);
    }

    @Override
    public PageResponse<KnowledgeArticle> findAll(int page, int size, String keyword, String category) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Specification<KnowledgeArticle> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            if (StringUtils.hasText(keyword)) {
                jakarta.persistence.criteria.Predicate titleLike = cb.like(root.get("title"), "%" + keyword + "%");
                jakarta.persistence.criteria.Predicate contentLike = cb.like(root.get("content"), "%" + keyword + "%");
                predicates.add(cb.or(titleLike, contentLike));
            }
            
            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        
        Page<KnowledgeArticle> articlePage = articleRepository.findAll(spec, pageable);
        
        return PageResponse.<KnowledgeArticle>builder()
                .content(articlePage.getContent())
                .totalElements(articlePage.getTotalElements())
                .totalPages(articlePage.getTotalPages())
                .currentPage(articlePage.getNumber())
                .pageSize(articlePage.getSize())
                .build();
    }

    @Override
    public List<KnowledgeArticle> findByAuthorId(UUID authorId) {
        return articleRepository.findByAuthorId(authorId);
    }

    @Override
    public List<KnowledgeArticle> findByCategory(String category) {
        return articleRepository.findByCategory(category);
    }

    @Override
    public List<KnowledgeArticle> findPopular(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("viewCount").descending());
        return articleRepository.findAll(pageable).getContent();
    }

    @Override
    @Transactional
    public KnowledgeArticle updateArticle(UUID id, KnowledgeArticle article) {
        log.info("更新知识文章: id={}", id);
        
        KnowledgeArticle existingArticle = articleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_NOT_FOUND));
        
        if (article.getTitle() != null) {
            existingArticle.setTitle(article.getTitle());
        }
        if (article.getContent() != null) {
            existingArticle.setContent(article.getContent());
        }
        if (article.getSummary() != null) {
            existingArticle.setSummary(article.getSummary());
        }
        if (article.getCategory() != null) {
            existingArticle.setCategory(article.getCategory());
        }
        
        return articleRepository.save(existingArticle);
    }

    @Override
    @Transactional
    public void deleteArticle(UUID id) {
        log.info("删除知识文章: id={}", id);
        articleRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void incrementViewCount(UUID id) {
        articleRepository.findById(id).ifPresent(article -> {
            article.setViewCount(article.getViewCount() + 1);
            articleRepository.save(article);
        });
    }

    @Override
    @Transactional
    public void addComment(UUID articleId, UUID userId, String content) {
        log.info("添加知识评论: articleId={}, userId={}", articleId, userId);
        
        KnowledgeComment comment = KnowledgeComment.builder()
                .articleId(articleId)
                .userId(userId)
                .content(content)
                .build();
        commentRepository.save(comment);
    }

    @Override
    @Transactional
    public void addTag(UUID articleId, String tag) {
        log.info("添加知识标签: articleId={}, tag={}", articleId, tag);
        
        KnowledgeTag knowledgeTag = KnowledgeTag.builder()
                .articleId(articleId)
                .tag(tag)
                .build();
        tagRepository.save(knowledgeTag);
    }

    @Override
    @Transactional
    public void removeTag(UUID articleId, String tag) {
        log.info("移除知识标签: articleId={}, tag={}", articleId, tag);
        tagRepository.deleteByArticleIdAndTag(articleId, tag);
    }

    @Override
    public List<String> getTags(UUID articleId) {
        return tagRepository.findByArticleId(articleId).stream()
                .map(KnowledgeTag::getTag)
                .collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeArticle> search(String keyword) {
        return articleRepository.search(keyword);
    }
}
