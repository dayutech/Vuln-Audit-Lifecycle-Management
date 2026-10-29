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
    List<Document> findByTaskId(@Nullable UUID taskId);
    List<Document> findByAuthorId(@Nullable UUID authorId);
    List<Document> findByStatus(@Nullable String status);
    List<Document> findByCategory(@Nullable String category);
    List<Document> findByCreatedBy(@Nullable UUID createdBy);
    boolean existsByTitle(@Nullable String title);

    @org.springframework.data.jpa.repository.Query("SELECT d FROM Document d WHERE d.title LIKE %:keyword% OR d.content LIKE %:keyword%")
    List<Document> search(@org.springframework.data.repository.query.Param("keyword") String keyword);

    /**
     * 行级加锁读取文档（SELECT ... FOR UPDATE）
     *
     * <p>用于文档版本号分配：同一文档的并发保存在这里串行化，
     * 避免两个请求都基于 version=N 生成同一份 N+1 快照。
     *
     * @param id 文档ID
     * @return 文档（不存在为空）
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT d FROM Document d WHERE d.id = :id")
    java.util.Optional<Document> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

}
