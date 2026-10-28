package com.vulnaudit.security;

import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户详情服务实现
 * 
 * 功能说明：
 * - 实现Spring Security的UserDetailsService接口
 * - 从数据库加载用户信息
 * - 转换为Spring Security的UserDetails对象
 * - 支持用户状态检查（活跃/暂停/停用）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    /**
     * 根据用户名加载用户详情
     * 
     * @param username 用户名
     * @return 用户详情
     * @throws UsernameNotFoundException 用户未找到异常
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("加载用户详情: username={}", username);
        
        // 从数据库查询用户
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("用户不存在: {}", username);
                    return new UsernameNotFoundException("用户不存在: " + username);
                });
        
        // 检查用户状态
        if (user.isSuspended()) {
            log.warn("用户账号已被暂停: {}", username);
            throw new UsernameNotFoundException("用户账号已被暂停: " + username);
        }
        
        if (user.isDeactivated()) {
            log.warn("用户账号已被停用: {}", username);
            throw new UsernameNotFoundException("用户账号已被停用: " + username);
        }
        
        // 从数据库加载用户的实际角色
        List<String> roles = userRoleRepository.findByUserId(user.getId()).stream()
                .map(userRole -> {
                    UUID roleId = userRole.getRoleId();
                    if (roleId == null) {
                        return null;
                    }
                    Role role = roleRepository.findById(roleId).orElse(null);
                    return role != null ? role.getCode() : null;
                })
                .filter(roleCode -> roleCode != null)
                .collect(Collectors.toList());
        
        // 构建权限列表：角色码统一转换为 ROLE_ 前缀的大写 authority，
        // 保证 hasRole('ADMIN') 与 getAuthorities() 手工判断口径一致
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(RoleAuthority::of)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        // 如果用户没有角色，默认赋予 ROLE_USER
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority(RoleAuthority.USER));
        }
        
        log.debug("用户 {} 加载了 {} 个角色: {}", username, authorities.size(), authorities);
        
        // 构建Spring Security的UserDetails对象
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                user.isActive(),
                true,
                true,
                true,
                authorities
        );
    }

}
