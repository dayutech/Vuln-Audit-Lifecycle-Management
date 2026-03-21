package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户服务接口
 * 
 * 功能说明：
 * - 定义用户管理的业务逻辑接口
 * - 提供用户CRUD操作
 * - 提供用户登录验证
 * - 提供密码加密功能
 */
public interface UserService {

    /**
     * 创建用户
     * 
     * @param user 用户实体
     * @return 创建后的用户实体
     */
    User createUser(User user);

    /**
     * 根据ID查询用户
     * 
     * @param id 用户ID
     * @return 用户实体
     */
    Optional<User> findById(UUID id);

    /**
     * 根据用户名查询用户
     * 
     * @param username 用户名
     * @return 用户实体
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据邮箱查询用户
     * 
     * @param email 邮箱
     * @return 用户实体
     */
    Optional<User> findByEmail(String email);

    /**
     * 分页查询用户列表
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param keyword 搜索关键词（可选）
     * @param status 用户状态（可选）
     * @return 分页用户列表
     */
    PageResponse<User> findAll(int page, int size, String keyword, String status);

    /**
     * 更新用户信息
     * 
     * @param id 用户ID
     * @param user 更新后的用户信息
     * @return 更新后的用户实体
     */
    User updateUser(UUID id, User user);

    /**
     * 更新用户状态
     * 
     * @param id 用户ID
     * @param status 新状态
     * @param operatorId 操作者ID
     * @return 更新后的用户实体
     */
    User updateStatus(UUID id, String status, UUID operatorId);

    /**
     * 删除用户（软删除，设置状态为deactivated）
     * 
     * @param id 用户ID
     * @param operatorId 操作者ID
     */
    void deleteUser(UUID id, UUID operatorId);

    /**
     * 验证用户登录
     * 
     * @param username 用户名
     * @param password 密码（明文）
     * @return 验证成功返回用户实体
     */
    User authenticate(String username, String password);

    /**
     * 修改密码
     * 
     * @param id 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否修改成功
     */
    boolean changePassword(UUID id, String oldPassword, String newPassword);

    /**
     * 重置密码（管理员操作）
     * 
     * @param id 用户ID
     * @param newPassword 新密码
     * @param operatorId 操作者ID
     */
    void resetPassword(UUID id, String newPassword, UUID operatorId);

    /**
     * 更新最后登录时间
     * 
     * @param id 用户ID
     */
    void updateLastLogin(UUID id);

    /**
     * 检查用户名是否存在
     * 
     * @param username 用户名
     * @return 是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     * 
     * @param email 邮箱
     * @return 是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 获取用户的所有角色代码
     * 
     * @param userId 用户ID
     * @return 角色代码列表
     */
    List<String> getUserRoleCodes(UUID userId);

    /**
     * 获取用户的所有权限代码
     * 
     * @param userId 用户ID
     * @return 权限代码列表
     */
    List<String> getUserPermissionCodes(UUID userId);

}
