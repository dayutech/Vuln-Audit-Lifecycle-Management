package com.vulnaudit.repository;

import com.vulnaudit.entity.ToolDownload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 工具下载记录Repository接口
 * 
 * 功能说明：
 * - 提供工具下载记录的数据访问操作
 * - 支持下载统计功能
 */
@Repository
public interface ToolDownloadRepository extends JpaRepository<ToolDownload, UUID> {

    /**
     * 按工具ID查询所有下载记录
     * 
     * @param toolId 工具ID
     * @return 下载记录列表
     */
    List<ToolDownload> findByToolIdOrderByDownloadedAtDesc(UUID toolId);

    /**
     * 按用户ID查询所有下载记录
     * 
     * @param userId 用户ID
     * @return 下载记录列表
     */
    List<ToolDownload> findByUserIdOrderByDownloadedAtDesc(UUID userId);

    /**
     * 统计工具下载次数
     * 
     * @param toolId 工具ID
     * @return 下载次数
     */
    long countByToolId(UUID toolId);

    /**
     * 统计指定时间范围内的下载次数
     * 
     * @param toolId 工具ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 下载次数
     */
    long countByToolIdAndDownloadedAtBetween(UUID toolId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 检查用户是否下载过该工具
     * 
     * @param toolId 工具ID
     * @param userId 用户ID
     * @return 是否下载过
     */
    boolean existsByToolIdAndUserId(UUID toolId, UUID userId);
}
