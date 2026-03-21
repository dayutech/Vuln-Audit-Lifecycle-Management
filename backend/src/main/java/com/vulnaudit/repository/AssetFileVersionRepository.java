package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetFileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产文件版本数据访问层接口
 * 
 * 功能说明：
 * - 提供文件版本实体的CRUD操作
 * - 提供版本查询相关的自定义方法
 */
@Repository
public interface AssetFileVersionRepository extends JpaRepository<AssetFileVersion, UUID> {

    /**
     * 根据文件ID查询所有版本
     * 
     * @param fileId 文件ID
     * @return 版本列表（按版本号倒序）
     */
    @Query("SELECT afv FROM AssetFileVersion afv WHERE afv.fileId = :fileId ORDER BY afv.version DESC")
    List<AssetFileVersion> findByFileIdOrderByVersionDesc(@Param("fileId") UUID fileId);

    /**
     * 根据文件ID和版本号查询版本
     * 
     * @param fileId 文件ID
     * @param version 版本号
     * @return 版本实体（如果存在）
     */
    Optional<AssetFileVersion> findByFileIdAndVersion(UUID fileId, Integer version);

    /**
     * 查询文件的最新版本
     * 
     * @param fileId 文件ID
     * @return 最新版本（如果存在）
     */
    @Query("SELECT afv FROM AssetFileVersion afv WHERE afv.fileId = :fileId ORDER BY afv.version DESC LIMIT 1")
    Optional<AssetFileVersion> findLatestVersion(@Param("fileId") UUID fileId);

    /**
     * 统计文件的版本数量
     * 
     * @param fileId 文件ID
     * @return 版本数量
     */
    long countByFileId(UUID fileId);

    /**
     * 删除文件的所有版本
     * 
     * @param fileId 文件ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM AssetFileVersion afv WHERE afv.fileId = :fileId")
    int deleteByFileId(@Param("fileId") UUID fileId);

    List<AssetFileVersion> findByFileIdOrderByCreatedAtDesc(UUID fileId);

}
