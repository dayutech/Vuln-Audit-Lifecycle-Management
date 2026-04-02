package com.vulnaudit.repository;

import com.vulnaudit.entity.Role;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 角色数据访问层接口
 *
 * 功能说明：
 * - 提供角色实体的CRUD操作
 * - 提供角色查询相关的自定义方法
 * - 支持角色代码唯一性检查
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID>, JpaSpecificationExecutor<Role> {

    /**
     * 根据角色代码查询角色
     * 用于根据角色代码获取角色信息
     *
     * @param code 角色代码
     * @return 角色实体（如果存在）
     */
    Optional<Role> findByCode(@Nullable String code);

    /**
     * 根据角色名称查询角色
     * 用于根据角色名称获取角色信息
     *
     * @param name 角色名称
     * @return 角色实体（如果存在）
     */
    Optional<Role> findByName(@Nullable String name);

    /**
     * 检查角色代码是否已存在
     * 用于角色创建时的代码唯一性验证
     *
     * @param code 角色代码
     * @return 如果角色代码已存在返回true，否则返回false
     */
    boolean existsByCode(@Nullable String code);

    /**
     * 检查是否存在系统内置角色
     * 用于验证系统角色完整性
     *
     * @param isSystem 是否系统内置
     * @return 如果存在符合条件的角色返回true
     */
    boolean existsByIsSystem(@Nullable Boolean isSystem);

}
