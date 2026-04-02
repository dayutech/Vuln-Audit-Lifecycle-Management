package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Permission;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 权限服务接口
 *
 * 功能说明：
 * - 定义权限管理的业务逻辑接口
 * - 提供权限CRUD操作
 * - 提供权限验证
 */
public interface PermissionService {

    /**
     * 创建权限
     *
     * @param permission 权限实体
     * @return 创建后的权限实体
     */
    @Nonnull
    Permission createPermission(@Nonnull Permission permission);

    /**
     * 根据ID查询权限
     *
     * @param id 权限ID
     * @return 权限实体
     */
    Optional<Permission> findById(@Nullable UUID id);

    /**
     * 根据权限代码查询权限
     *
     * @param code 权限代码
     * @return 权限实体
     */
    Optional<Permission> findByCode(@Nullable String code);

    /**
     * 查询所有权限
     *
     * @return 权限列表
     */
    @Nonnull
    List<Permission> findAll();

    /**
     * 分页查询权限列表
     *
     * @param page 页码
     * @param size 每页大小
     * @param keyword 搜索关键词
     * @return 分页权限列表
     */
    @Nonnull
    PageResponse<Permission> findAll(int page, int size, @Nullable String keyword);

    /**
     * 更新权限
     *
     * @param id 权限ID
     * @param permission 更新后的权限信息
     * @return 更新后的权限实体
     */
    @Nonnull
    Permission updatePermission(@Nullable UUID id, @Nonnull Permission permission);

    /**
     * 删除权限
     *
     * @param id 权限ID
     */
    void deletePermission(@Nullable UUID id);

    /**
     * 检查权限代码是否存在
     *
     * @param code 权限代码
     * @return 是否存在
     */
    boolean existsByCode(@Nullable String code);

    /**
     * 验证用户是否有指定API的访问权限
     *
     * @param userId 用户ID
     * @param apiPath API路径
     * @param httpMethod HTTP方法
     * @return 是否有权限
     */
    boolean hasPermission(@Nullable UUID userId, @Nullable String apiPath, @Nullable String httpMethod);

    /**
     * 获取用户的所有权限代码
     *
     * @param userId 用户ID
     * @return 权限代码列表
     */
    @Nonnull
    List<String> getUserPermissionCodes(@Nullable UUID userId);

}
