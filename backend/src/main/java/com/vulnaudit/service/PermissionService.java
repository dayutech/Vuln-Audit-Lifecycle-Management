package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Permission;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 权限服务接口
 *
 * 功能说明：
 * - 定义权限管理的业务逻辑接口
 * - 提供权限CRUD操作
 * - 提供权限验证与用户直接授权
 *
 * <p><b>双轨（角色轨 + 权限点轨）</b>：当前 API 准入仍由控制器的
 * {@code @PreAuthorize("hasRole(...)")} 负责，本服务提供的是「权限点轨」数据面
 * ——权限点清单、角色并集、用户单独授权（含 {@code expires_at}）。
 * 权限点集合即为后续 ABAC 的基础：可由 {@code (apiPath, httpMethod)} 反查所需权限编码，
 * 再判断用户有效权限集合是否覆盖，从而在不改动既有注解语义的前提下逐步替换硬编码角色名。
 *
 * <p>分配类接口约束：存在性必须校验（用户/角色/权限点不存在直接报错），
 * 重复调用必须幂等（不产生重复关联行，不报错）。
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
     * 获取用户的有效权限代码集合（角色并集 + 用户单独授权，已排除过期授权）。
     *
     * @param userId 用户ID
     * @return 权限代码列表（去重、字典序，便于前端与断言比对）
     */
    @Nonnull
    List<String> getUserPermissionCodes(@Nullable UUID userId);

    /**
     * 直接授予用户某个权限点（绕过角色）。
     *
     * <p>幂等语义：
     * <ul>
     *   <li>无关联 → 新建关联；</li>
     *   <li>已有关联且未过期 → 保持不变（即使传入新的过期时间也不续期，避免误放大权限）；</li>
     *   <li>已有关联但已过期 → 复用该行，刷新 {@code expires_at} 重新生效。</li>
     * </ul>
     * 用户或权限点不存在时抛出业务异常。
     *
     * @param userId       用户ID
     * @param permissionId 权限点ID
     * @param grantedBy    授权人ID（为空时使用操作者自身）
     * @param expiresAt    过期时间，可为 {@code null} 表示永久有效
     * @return 关联是否发生了实际变更（新建或重新激活）
     */
    boolean assignPermissionToUser(@Nullable UUID userId,
                                   @Nullable UUID permissionId,
                                   @Nullable UUID grantedBy,
                                   @Nullable LocalDateTime expiresAt);

    /**
     * 撤销用户直接被授予的权限点。
     *
     * <p>幂等：关联不存在时静默返回 {@code false}；用户或权限点不存在时抛出业务异常。
     *
     * @param userId       用户ID
     * @param permissionId 权限点ID
     * @return 是否实际删除了关联
     */
    boolean removePermissionFromUser(@Nullable UUID userId, @Nullable UUID permissionId);

    /**
     * 查询用户当前生效的直接授权记录（用于管理端展示过期时间）。
     *
     * @param userId 用户ID
     * @return 生效中的直接授权列表
     */
    @Nonnull
    List<com.vulnaudit.entity.UserPermission> getUserDirectPermissions(@Nullable UUID userId);

}
