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
     * 取同一工具下最大版本序号，用于自增
     *
     * @param toolId 工具ID
     * @return 最新版本
     */
    Optional<ToolVersion> findFirstByToolIdOrderByVersionNoDesc(UUID toolId);

    /**
     * 检查同一工具下版本序号是否存在（并发冲突预检）
     */
    boolean existsByToolIdAndVersionNo(UUID toolId, Integer versionNo);

    /**
     * 按版本ID + 工具ID查询（下载时校验归属）
     */
    Optional<ToolVersion> findByIdAndToolId(UUID id, UUID toolId);

    /**
     * 统计工具版本数量
     * 
     * @param toolId 工具ID
     * @return 版本数量
     */
    long countByToolId(UUID toolId);
}
