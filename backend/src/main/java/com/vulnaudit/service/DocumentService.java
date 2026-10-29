package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Document;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文档服务接口
 */
public interface DocumentService {

    @Nonnull
    Document createDocument(@Nonnull Document document);

    Optional<Document> findById(@Nullable UUID id);

    @Nonnull
    PageResponse<Document> findAll(int page, int size, @Nullable String keyword, @Nullable String category, @Nullable UUID assetId, @Nullable UUID taskId);

    @Nonnull
    List<Document> findByAssetId(@Nullable UUID assetId);

    @Nonnull
    List<Document> findByTaskId(@Nullable UUID taskId);

    @Nonnull
    List<Document> findByCategory(@Nullable String category);

    @Nonnull
    Document updateDocument(@Nullable UUID id, @Nonnull Document document);

    /**
     * 更新文档并在正文变化时原子写入版本快照
     *
     * <p>并发语义（沿用工具版本的三层方案）：
     * <ul>
     *     <li>行级锁 {@code DocumentRepository#findByIdForUpdate} 串行化同一文档的版本号分配；</li>
     *     <li>{@code saveAndFlush} 把 {@code (document_id, version)} 唯一键冲突提前到可控范围；</li>
     *     <li>冲突时在新事务中重算版本号，最多 3 次；耗尽抛 409「文档版本冲突，请重试」。</li>
     * </ul>
     *
     * @param id         文档ID
     * @param patch      更新内容（仅非空字段生效）
     * @param operatorId 操作者ID（写入快照 created_by）
     * @return 更新后的文档
     */
    @Nonnull
    Document updateDocumentWithSnapshot(@Nullable UUID id, @Nonnull Document patch, @Nullable UUID operatorId);

    /**
     * 回滚到指定历史版本
     *
     * <p>语义：历史版本不可变，只新增一条快照（version = max + 1，内容取目标版本），
     * 并把 documents 当前内容/版本指向该新快照；快照 changelog 标注 {@code rollback from vN}
     * 以便审计追溯。版本号分配沿用行级锁 + saveAndFlush + 有界重试，冲突耗尽抛 409。
     *
     * @param id         文档ID
     * @param version    目标历史版本号
     * @param operatorId 操作者ID（写入新快照 created_by）
     * @return 回滚后的文档
     */
    @Nonnull
    Document rollbackToVersion(@Nullable UUID id, @Nullable Integer version, @Nullable UUID operatorId);

    void deleteDocument(@Nullable UUID id);

    boolean existsByTitle(@Nullable String title);

    @Nonnull
    List<Document> search(@Nullable String keyword);

    void grantAccess(@Nullable UUID documentId, @Nullable UUID userId, @Nullable String accessType);

    void revokeAccess(@Nullable UUID documentId, @Nullable UUID userId);

    boolean hasAccess(@Nullable UUID documentId, @Nullable UUID userId, @Nullable String accessType);

    @Nonnull
    List<Document> findByCreatedBy(@Nullable UUID createdBy);
}
