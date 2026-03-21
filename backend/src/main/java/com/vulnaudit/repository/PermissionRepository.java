package com.vulnaudit.repository;

import com.vulnaudit.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 权限数据访问层接口
 * 
 * 功能说明：
 * - 提供权限实体的CRUD操作
 * - 提供权限查询相关的自定义方法
 * - 支持权限代码唯一性检查
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID>, JpaSpecificationExecutor<Permission> {

    /**
     * 根据权限代码查询权限
     * 用于根据权限代码获取权限信息
     * 
     * @param code 权限代码
     * @return 权限实体（如果存在）
     */
    Optional<Permission> findByCode(String code);

    /**
     * 根据API路径和HTTP方法查询权限
     * 用于API权限验证
     * 
     * @param apiPath API路径
     * @param httpMethod HTTP方法
     * @return 权限实体（如果存在）
     */
    Optional<Permission> findByApiPathAndHttpMethod(String apiPath, String httpMethod);

    /**
     * 检查权限代码是否已存在
     * 用于权限创建时的代码唯一性验证
     * 
     * @param code 权限代码
     * @return 如果权限代码已存在返回true，否则返回false
     */
    boolean existsByCode(String code);

}
