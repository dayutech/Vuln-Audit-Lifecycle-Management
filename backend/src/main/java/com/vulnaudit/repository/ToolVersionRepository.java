package com.vulnaudit.repository;

import com.vulnaudit.entity.ToolVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 工具版本Repository接口
 * 
 * 功能说明：
 * - 提供工具版本的数据访问操作
 * - 支持按工具ID和版本号查询
 */
@Repository
public interface ToolVersionRepository extends JpaRepository<ToolVersion, UUID> {

    /**
     * 按工具ID查询所有版本
     * 
     * @param toolId 工具ID
     * @return 版本列表
     */
    List<ToolVersion> findByToolIdOrderByCreatedAtDesc(UUID toolId);

    /**
     * 按工具ID和版本号查询
     * 
     * @param toolId 工具ID
     * @param version 版本号
     * @return 版本实体
     */
    Optional<ToolVersion> findByToolIdAndVersion(UUID toolId, String version);

    /**
     * 检查版本是否存在
     * 
     * @param toolId 工具ID
     * @param version 版本号
     * @return 是否存在
     */
    boolean existsByToolIdAndVersion(UUID toolId, String version);

    /**
     * 统计工具版本数量
     * 
     * @param toolId 工具ID
     * @return 版本数量
     */
    long countByToolId(UUID toolId);
}
