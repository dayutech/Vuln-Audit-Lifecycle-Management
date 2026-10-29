package com.vulnaudit.repository;

import com.vulnaudit.entity.Tool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolRepository extends JpaRepository<Tool, UUID>, JpaSpecificationExecutor<Tool> {

    List<Tool> findByCategory(String category);
    List<Tool> findByCreatedBy(UUID createdBy);
    List<Tool> findByIsOfficial(Boolean isOfficial);
    Optional<Tool> findByName(String name);
    boolean existsByName(String name);
    
    @Query("SELECT t FROM Tool t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Tool> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 行级加锁读取工具（SELECT ... FOR UPDATE）
     *
     * <p>用于工具版本号分配：同一工具的并发上传在这里串行化，
     * 避免「先查 max 再插入」在同一时刻算出相同的 version_no。
     *
     * @param id 工具ID
     * @return 工具（不存在为空）
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tool t WHERE t.id = :id")
    Optional<Tool> findByIdForUpdate(@Param("id") UUID id);

}
