package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产分组数据访问层接口
 * 
 * 功能说明：
 * - 提供资产分组实体的CRUD操作
 * - 提供分组查询相关的自定义方法
 */
@Repository
public interface AssetGroupRepository extends JpaRepository<AssetGroup, UUID> {

    /**
     * 根据分组名称查询分组
     * 
     * @param name 分组名称
     * @return 分组实体（如果存在）
     */
    Optional<AssetGroup> findByName(String name);

    /**
     * 根据分组类型查询分组列表
     * 
     * @param type 分组类型
     * @return 分组列表
     */
    List<AssetGroup> findByType(String type);

    /**
     * 根据分组类型分页查询分组列表
     * 
     * @param type 分组类型
     * @param pageable 分页参数
     * @return 分组分页列表
     */
    Page<AssetGroup> findByType(String type, Pageable pageable);

    /**
     * 检查分组名称是否已存在
     * 
     * @param name 分组名称
     * @return 如果已存在返回true
     */
    boolean existsByName(String name);

}
