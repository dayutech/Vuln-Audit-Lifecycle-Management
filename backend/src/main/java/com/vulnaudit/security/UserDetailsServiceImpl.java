package com.vulnaudit.security;

import com.vulnaudit.entity.User;
import com.vulnaudit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

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

    /**
     * 根据用户名加载用户详情
     * 
     * @param username 用户名
     * @return 用户详情
     * @throws UsernameNotFoundException 用户未找到异常
     */
    @Override
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
        
        // 构建Spring Security的UserDetails对象
        // 注意：权限列表暂时为空，后续会从角色和权限表加载
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                user.isActive(),
                true,
                true,
                true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

}
