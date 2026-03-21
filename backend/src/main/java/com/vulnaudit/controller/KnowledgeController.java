package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.KnowledgeArticle;
import com.vulnaudit.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 知识库控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
@Tag(name = "知识库", description = "知识文章CRUD、评论、标签等接口")
@SecurityRequirement(name = "bearerAuth")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping
    @Operation(summary = "创建知识文章", description = "创建新知识文章")
    public ResponseEntity<ApiResponse<KnowledgeArticle>> createArticle(@Valid @RequestBody KnowledgeArticle article) {
        log.info("创建知识文章: title={}", article.getTitle());
        
        KnowledgeArticle createdArticle = knowledgeService.createArticle(article);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdArticle));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取知识文章详情", description = "根据ID获取知识文章详细信息")
    public ResponseEntity<ApiResponse<KnowledgeArticle>> getArticle(@PathVariable UUID id) {
        log.info("获取知识文章详情: id={}", id);
        
        // 增加浏览次数
        knowledgeService.incrementViewCount(id);
        
        return knowledgeService.findById(id)
                .map(article -> ResponseEntity.ok(ApiResponse.success(article)))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "知识文章不存在")));
    }

    @GetMapping
    @Operation(summary = "分页查询知识文章列表", description = "支持按关键词、分类筛选")
    public ResponseEntity<ApiResponse<PageResponse<KnowledgeArticle>>> listArticles(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "分类") @RequestParam(required = false) String category) {
        
        log.info("分页查询知识文章: page={}, size={}, keyword={}, category={}", page, size, keyword, category);
        
        PageResponse<KnowledgeArticle> result = knowledgeService.findAll(page, size, keyword, category);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/author/{authorId}")
    @Operation(summary = "获取作者的文章", description = "获取指定作者的所有文章")
    public ResponseEntity<ApiResponse<List<KnowledgeArticle>>> listArticlesByAuthor(@PathVariable UUID authorId) {
        log.info("获取作者的文章: authorId={}", authorId);
        
        List<KnowledgeArticle> articles = knowledgeService.findByAuthorId(authorId);
        return ResponseEntity.ok(ApiResponse.success(articles));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "按分类获取文章", description = "获取指定分类的所有文章")
    public ResponseEntity<ApiResponse<List<KnowledgeArticle>>> listArticlesByCategory(@PathVariable String category) {
        log.info("按分类获取文章: category={}", category);
        
        List<KnowledgeArticle> articles = knowledgeService.findByCategory(category);
        return ResponseEntity.ok(ApiResponse.success(articles));
    }

    @GetMapping("/popular")
    @Operation(summary = "获取热门文章", description = "获取浏览量最高的文章")
    public ResponseEntity<ApiResponse<List<KnowledgeArticle>>> getPopularArticles(
            @Parameter(description = "数量限制") @RequestParam(defaultValue = "10") int limit) {
        
        log.info("获取热门文章: limit={}", limit);
        
        List<KnowledgeArticle> articles = knowledgeService.findPopular(limit);
        return ResponseEntity.ok(ApiResponse.success(articles));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新知识文章", description = "更新知识文章信息")
    public ResponseEntity<ApiResponse<KnowledgeArticle>> updateArticle(
            @PathVariable UUID id,
            @Valid @RequestBody KnowledgeArticle article) {
        
        log.info("更新知识文章: id={}", id);
        
        KnowledgeArticle updatedArticle = knowledgeService.updateArticle(id, article);
        return ResponseEntity.ok(ApiResponse.success(updatedArticle));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识文章", description = "删除知识文章")
    public ResponseEntity<ApiResponse<Void>> deleteArticle(@PathVariable UUID id) {
        log.info("删除知识文章: id={}", id);
        
        knowledgeService.deleteArticle(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索知识文章", description = "全文搜索知识文章")
    public ResponseEntity<ApiResponse<List<KnowledgeArticle>>> searchArticles(
            @Parameter(description = "搜索关键词") @RequestParam String keyword) {
        
        log.info("搜索知识文章: keyword={}", keyword);
        
        List<KnowledgeArticle> articles = knowledgeService.search(keyword);
        return ResponseEntity.ok(ApiResponse.success(articles));
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "添加文章评论", description = "为文章添加评论")
    public ResponseEntity<ApiResponse<Void>> addComment(
            @PathVariable UUID id,
            @Parameter(description = "评论内容") @RequestParam String content) {
        
        log.info("添加文章评论: articleId={}", id);
        
        knowledgeService.addComment(id, null, content);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/tags")
    @Operation(summary = "获取文章标签", description = "获取文章的所有标签")
    public ResponseEntity<ApiResponse<List<String>>> getTags(@PathVariable UUID id) {
        log.info("获取文章标签: articleId={}", id);
        
        List<String> tags = knowledgeService.getTags(id);
        return ResponseEntity.ok(ApiResponse.success(tags));
    }

    @PostMapping("/{id}/tags")
    @Operation(summary = "添加文章标签", description = "为文章添加标签")
    public ResponseEntity<ApiResponse<Void>> addTag(
            @PathVariable UUID id,
            @Parameter(description = "标签") @RequestParam String tag) {
        
        log.info("添加文章标签: articleId={}, tag={}", id, tag);
        
        knowledgeService.addTag(id, tag);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}/tags")
    @Operation(summary = "移除文章标签", description = "移除文章标签")
    public ResponseEntity<ApiResponse<Void>> removeTag(
            @PathVariable UUID id,
            @Parameter(description = "标签") @RequestParam String tag) {
        
        log.info("移除文章标签: articleId={}, tag={}", id, tag);
        
        knowledgeService.removeTag(id, tag);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
