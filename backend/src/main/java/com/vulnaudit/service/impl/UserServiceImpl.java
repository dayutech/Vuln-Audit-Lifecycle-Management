package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Permission;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.User;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.*;
import com.vulnaudit.service.UserService;
import com.vulnaudit.util.Paging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 * 
 * 功能说明：
 * - 实现用户管理的业务逻辑
 * - 提供用户CRUD操作
 * - 提供用户登录验证
 * - 提供密码加密功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建用户
     * 对密码进行加密存储
     */
    @Override
    @Transactional
    public User createUser(User user) {
        log.info("创建用户: username={}, email={}", user.getUsername(), user.getEmail());
        
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(user.getUsername())) {
            log.warn("用户名已存在: {}", user.getUsername());
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        
        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(user.getEmail())) {
            log.warn("邮箱已存在: {}", user.getEmail());
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        
        // 加密密码
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        
        // 设置默认状态
        if (user.getStatus() == null) {
            user.setStatus("active");
        }
        
        User savedUser = userRepository.save(user);
        log.info("用户创建成功: id={}, username={}", savedUser.getId(), savedUser.getUsername());
        
        return savedUser;
    }

    /**
     * 根据ID查询用户
     */
    @Override
    public Optional<User> findById(UUID id) {
        log.debug("查询用户: id={}", id);
        if (id == null) {
            return Optional.empty();
        }
        return userRepository.findById(id);
    }

    /**
     * 根据用户名查询用户
     */
    @Override
    public Optional<User> findByUsername(String username) {
        log.debug("查询用户: username={}", username);
        return userRepository.findByUsername(username);
    }

    /**
     * 根据邮箱查询用户
     */
    @Override
    public Optional<User> findByEmail(String email) {
        log.debug("查询用户: email={}", email);
        return userRepository.findByEmail(email);
    }

    /**
     * 分页查询用户列表
     */
    @Override
    public PageResponse<User> findAll(int page, int size, String keyword, String status) {
        log.debug("分页查询用户: page={}, size={}, keyword={}, status={}", page, size, keyword, status);
        
        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        Page<User> userPage;
        
        if (StringUtils.hasText(keyword) && StringUtils.hasText(status)) {
            userPage = userRepository.findAll(
                (root, query, cb) -> cb.and(
                    cb.or(
                        cb.like(root.get("username"), "%" + keyword + "%"),
                        cb.like(root.get("email"), "%" + keyword + "%"),
                        cb.like(root.get("fullName"), "%" + keyword + "%")
                    ),
                    cb.equal(root.get("status"), status)
                ),
                pageable
            );
        } else if (StringUtils.hasText(keyword)) {
            userPage = userRepository.findAll(
                (root, query, cb) -> cb.or(
                    cb.like(root.get("username"), "%" + keyword + "%"),
                    cb.like(root.get("email"), "%" + keyword + "%"),
                    cb.like(root.get("fullName"), "%" + keyword + "%")
                ),
                pageable
            );
        } else if (StringUtils.hasText(status)) {
            userPage = userRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("status"), status),
                pageable
            );
        } else {
            userPage = userRepository.findAll(pageable);
        }
        
        return PageResponse.<User>builder()
                .content(userPage.getContent())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .currentPage(userPage.getNumber())
                .pageSize(userPage.getSize())
                .build();
    }

    /**
     * 更新用户信息
     */
    @Override
    @Transactional
    public User updateUser(UUID id, User user) {
        log.info("更新用户: id={}", id);
        
        if (id == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("用户不存在: id={}", id);
                    return new BusinessException(ErrorCode.USER_NOT_FOUND);
                });
        
        // 更新可修改的字段
        if (user.getFullName() != null) {
            existingUser.setFullName(user.getFullName());
        }
        if (user.getEmail() != null && !user.getEmail().equals(existingUser.getEmail())) {
            if (userRepository.existsByEmail(user.getEmail())) {
                log.warn("邮箱已存在: {}", user.getEmail());
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            existingUser.setEmail(user.getEmail());
        }
        if (user.getPhone() != null) {
            existingUser.setPhone(user.getPhone());
        }
        if (user.getAvatar() != null) {
            existingUser.setAvatar(user.getAvatar());
        }
        if (user.getPosition() != null) {
            existingUser.setPosition(user.getPosition());
        }
        
        User updatedUser = userRepository.save(Objects.requireNonNull(existingUser));
        log.info("用户更新成功: id={}", updatedUser.getId());
        
        return updatedUser;
    }

    /**
     * 更新用户状态
     */
    @Override
    @Transactional
    public User updateStatus(UUID id, String status, UUID operatorId) {
        log.info("更新用户状态: id={}, status={}, operatorId={}", id, status, operatorId);
        
        if (id == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("用户不存在: id={}", id);
                    return new BusinessException(ErrorCode.USER_NOT_FOUND);
                });
        
        // 验证状态值
        if (!Arrays.asList("active", "suspended", "deactivated").contains(status)) {
            log.warn("无效的用户状态: {}", status);
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        
        user.setStatus(status);
        User updatedUser = userRepository.save(user);
        log.info("用户状态更新成功: id={}, status={}", updatedUser.getId(), status);
        
        return updatedUser;
    }

    /**
     * 删除用户（软删除）
     */
    @Override
    @Transactional
    public void deleteUser(UUID id, UUID operatorId) {
        log.info("删除用户: id={}, operatorId={}", id, operatorId);
        
        if (id == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("用户不存在: id={}", id);
                    return new BusinessException(ErrorCode.USER_NOT_FOUND);
                });
        
        user.setStatus("deactivated");
        userRepository.save(user);
        
        log.info("用户已停用: id={}", id);
    }

    /**
     * 验证用户登录
     */
    @Override
    public User authenticate(String username, String password) {
        log.info("用户登录验证: username={}", username);
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("用户不存在: username={}", username);
                    return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });
        
        // 检查用户状态
        if (!user.isActive()) {
            log.warn("用户账号未激活: username={}", username);
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
        
        // 验证密码
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("密码错误: username={}", username);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        
        log.info("用户登录验证成功: username={}", username);
        return user;
    }

    /**
     * 修改密码
     */
    @Override
    @Transactional
    public boolean changePassword(UUID id, String oldPassword, String newPassword) {
        log.info("修改密码: userId={}", id);
        
        if (id == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("用户不存在: id={}", id);
                    return new BusinessException(ErrorCode.USER_NOT_FOUND);
                });
        
        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            log.warn("旧密码错误: userId={}", id);
            throw new BusinessException(ErrorCode.INVALID_OLD_PASSWORD);
        }
        
        // 更新密码
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        log.info("密码修改成功: userId={}", id);
        return true;
    }

    /**
     * 重置密码（管理员操作）
     */
    @Override
    @Transactional
    public void resetPassword(UUID id, String newPassword, UUID operatorId) {
        log.info("重置密码: userId={}, operatorId={}", id, operatorId);
        
        if (id == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("用户不存在: id={}", id);
                    return new BusinessException(ErrorCode.USER_NOT_FOUND);
                });
        
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        log.info("密码重置成功: userId={}", id);
    }

    /**
     * 更新最后登录时间
     */
    @Override
    @Transactional
    public void updateLastLogin(UUID id) {
        log.debug("更新最后登录时间: userId={}", id);
        
        userRepository.findById(Objects.requireNonNull(id)).ifPresent(user -> {
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(Objects.requireNonNull(user));
        });
    }

    /**
     * 检查用户名是否存在
     */
    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * 检查邮箱是否存在
     */
    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 获取用户的所有角色代码
     */
    @Override
    public List<String> getUserRoleCodes(UUID userId) {
        log.debug("获取用户角色代码: userId={}", userId);
        
        List<UUID> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        return roleRepository.findAllById(roleIds).stream()
                .map(Role::getCode)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户的所有权限代码
     */
    @Override
    public List<String> getUserPermissionCodes(UUID userId) {
        log.debug("获取用户权限代码: userId={}", userId);
        
        Set<String> permissionCodes = new HashSet<>();
        
        // 获取直接授予用户的权限
        List<UUID> directPermissionIds = userPermissionRepository.findValidPermissionIdsByUserId(userId, LocalDateTime.now());
        if (!directPermissionIds.isEmpty()) {
            permissionRepository.findAllById(directPermissionIds).stream()
                    .map(Permission::getCode)
                    .forEach(permissionCodes::add);
        }
        
        // 获取通过角色获得的权限
        List<UUID> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        for (UUID roleId : roleIds) {
            List<UUID> rolePermissionIds = rolePermissionRepository.findPermissionIdsByRoleId(roleId);
            if (!rolePermissionIds.isEmpty()) {
                permissionRepository.findAllById(rolePermissionIds).stream()
                        .map(Permission::getCode)
                        .forEach(permissionCodes::add);
            }
        }
        
        return new ArrayList<>(permissionCodes);
    }

}
