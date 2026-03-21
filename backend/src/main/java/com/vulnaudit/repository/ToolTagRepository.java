package com.vulnaudit.repository;

import com.vulnaudit.entity.ToolTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 工具标签Repository接口
 * 
 * 功能说明：
 * - 提供工具标签的数据访问操作
 * - 支持按工具ID和标签名查询
 */
@Repository
public interface ToolTagRepository extends JpaRepository<ToolTag, UUID> {

    /**
     * 按工具ID查询所有标签
     * 
     * @param toolId 工具ID
     * @return 标签列表
     */
    List<ToolTag> findByToolId(UUID toolId);

    /**
     * 按标签名查询所有工具标签
     * 
     * @param tag 标签名
     * @return 标签列表
     */
    List<ToolTag> findByTag(String tag);

    /**
     * 按工具ID删除所有标签
     * 
     * @param toolId 工具ID
     */
    void deleteByToolId(UUID toolId);

    /**
     * 按工具ID和标签名删除
     * 
     * @param toolId 工具ID
     * @param tag 标签名
     */
    void deleteByToolIdAndTag(UUID toolId, String tag);
}
