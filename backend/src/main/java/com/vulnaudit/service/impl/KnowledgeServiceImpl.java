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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

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
    public Optional<KnowledgeArticle> findById(@Nullable UUID id) {
        return id != null ? articleRepository.findById(id) : Optional.empty();
    }

    @Override
    public PageResponse<KnowledgeArticle> findAll(int page, int size, @Nullable String keyword, @Nullable String category) {
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
    public List<KnowledgeArticle> findByAuthorId(@Nullable UUID authorId) {
        return authorId != null ? articleRepository.findByAuthorId(authorId) : List.of();
    }

    @Override
    public List<KnowledgeArticle> findByCategory(@Nullable String category) {
        return category != null ? articleRepository.findByCategory(category) : List.of();
    }

    @Override
    public List<KnowledgeArticle> findPopular(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("viewCount").descending());
        return articleRepository.findAll(pageable).getContent();
    }

    @Override
    @Transactional
    public KnowledgeArticle updateArticle(@Nullable UUID id, KnowledgeArticle article) {
        log.info("更新知识文章: id={}", id);

        if (id == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

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

        return articleRepository.save(Objects.requireNonNull(existingArticle));
    }

    @Override
    @Transactional
    public void deleteArticle(@Nullable UUID id) {
        log.info("删除知识文章: id={}", id);
        if (id != null) {
            articleRepository.deleteById(id);
        }
    }

    @Override
    @Transactional
    public void incrementViewCount(@Nullable UUID id) {
        if (id != null) {
            articleRepository.findById(id).ifPresent(article -> {
                article.setViewCount(article.getViewCount() + 1);
                articleRepository.save(article);
            });
        }
    }

    @Override
    @Transactional
    public void addComment(@Nullable UUID articleId, @Nullable UUID userId, @Nullable String content) {
        log.info("添加知识评论: articleId={}, userId={}", articleId, userId);

        if (articleId == null || userId == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        KnowledgeComment comment = KnowledgeComment.builder()
                .articleId(articleId)
                .userId(userId)
                .content(content)
                .build();
        commentRepository.save(Objects.requireNonNull(comment));
    }

    @Override
    @Transactional
    public void addTag(@Nullable UUID articleId, @Nullable String tag) {
        log.info("添加知识标签: articleId={}, tag={}", articleId, tag);

        if (articleId == null || tag == null) {
            return;
        }

        KnowledgeTag knowledgeTag = KnowledgeTag.builder()
                .articleId(articleId)
                .tag(tag)
                .build();
        tagRepository.save(Objects.requireNonNull(knowledgeTag));
    }

    @Override
    @Transactional
    public void removeTag(@Nullable UUID articleId, @Nullable String tag) {
        log.info("移除知识标签: articleId={}, tag={}", articleId, tag);
        if (articleId != null && tag != null) {
            tagRepository.deleteByArticleIdAndTag(articleId, tag);
        }
    }

    @Override
    public List<String> getTags(@Nullable UUID articleId) {
        if (articleId == null) {
            return List.of();
        }
        return tagRepository.findByArticleId(articleId).stream()
                .map(KnowledgeTag::getTag)
                .collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeArticle> search(@Nullable String keyword) {
        return keyword != null ? articleRepository.search(keyword) : List.of();
    }
}
