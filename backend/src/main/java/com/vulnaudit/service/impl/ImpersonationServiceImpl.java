package com.vulnaudit.service.impl;

import com.vulnaudit.config.ImpersonationProperties;
import com.vulnaudit.dto.ImpersonateRequest;
import com.vulnaudit.dto.ImpersonateResponse;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.UserRoleRepository;
import com.vulnaudit.security.JwtUtils;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.ImpersonationService;
import com.vulnaudit.service.UserService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 管理员模拟视角实现（t90）
 *
 * <p>审计归属规则：
 * <ul>
 *   <li>{@code IMPERSONATE_START}：user_id = 真实操作者（ADMIN），resourceType=user，resourceId=目标用户，
 *       details 含 {@code target=} 与 {@code reason=}；</li>
 *   <li>{@code IMPERSONATE_STOP}：user_id = 真实操作者，resourceType=user，resourceId=目标用户；</li>
 *   <li>模拟期间产生的<b>业务审计</b>：user_id = 目标用户（真实执行者），details 追加
 *       {@code impersonatedBy=<actorId>}（由 {@code AuditTrailInterceptor} 统一追加）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImpersonationServiceImpl implements ImpersonationService {

    private final ImpersonationProperties properties;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final JwtUtils jwtUtils;
    private final AuditLogService auditLogService;

    @Override
    // 注意：这里必须可写。方法内要写 IMPERSONATE_START 审计，readOnly=true 时 Hibernate 会以
    // FlushMode.MANUAL 忽略插入（不报错也不落库，t94 线上实测发现）；H2 单测因测试事务非只读而漏检。
    @Transactional
    public ImpersonateResponse impersonate(@Nullable UUID actorId, ImpersonateRequest request) {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "管理员模拟视角功能已关闭（app.security.impersonation.enabled=false）");
        }
        User actor = requireActor(actorId);
        if (request == null || request.userId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标用户不能为空");
        }

        User target = userRepository.findById(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "目标用户不存在: " + request.userId()));

        if (!"active".equalsIgnoreCase(target.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "目标用户已停用，无法模拟: " + target.getUsername());
        }

        List<String> roleCodes = roleCodesOf(target.getId());
        if (roleCodes.stream().anyMatch(code -> "admin".equalsIgnoreCase(code))) {
            throw new BusinessException(ErrorCode.CONFLICT, "不能模拟管理员账号，请直接登录该账号");
        }
        if (Objects.equals(actor.getId(), target.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "不能模拟自己");
        }

        long ttlMillis = Math.max(60L, properties.getTtlSeconds()) * 1000L;
        List<GrantedAuthority> authorities = roleCodes.stream()
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority(toAuthority(code)))
                .toList();

        String token = jwtUtils.generateImpersonationToken(target.getUsername(), authorities, actor.getId(), ttlMillis);
        Date expiration = jwtUtils.getExpiration(token);

        String reason = request.reason() == null || request.reason().isBlank() ? "(未填写)" : request.reason().trim();
        auditLogService.log(actor.getId(), "IMPERSONATE_START", "user", target.getId(),
                truncate("模拟开始: actor=" + actor.getUsername() + "(" + actor.getId() + "), target="
                        + target.getUsername() + "(" + target.getId() + "), ttlSeconds="
                        + properties.getTtlSeconds() + ", reason=" + reason, 500));

        log.warn("管理员模拟视角开启: actor={}({}), target={}({})", actor.getUsername(), actor.getId(),
                target.getUsername(), target.getId());

        return new ImpersonateResponse(
                token,
                Instant.ofEpochMilli(expiration.getTime()),
                new ImpersonateResponse.Target(target.getId(), target.getUsername(), roleCodes),
                new ImpersonateResponse.Actor(actor.getId(), actor.getUsername()));
    }

    @Override
    // 同 impersonate()：内部写 IMPERSONATE_STOP 审计，必须可写事务。
    @Transactional
    public UUID stop(@Nullable UUID actorId, @Nullable String targetUsername) {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "管理员模拟视角功能已关闭（app.security.impersonation.enabled=false）");
        }
        User actor = requireActor(actorId);
        UUID targetId = targetUsername == null ? null
                : userService.findByUsername(targetUsername).map(User::getId).orElse(null);

        auditLogService.log(actor.getId(), "IMPERSONATE_STOP", "user", targetId,
                truncate("模拟结束: actor=" + actor.getUsername() + "(" + actor.getId() + "), target="
                        + targetUsername + "(" + targetId + ")", 500));

        log.info("管理员模拟视角结束: actor={}, target={}", actor.getUsername(), targetUsername);
        return targetId;
    }

    // ------------------------------------------------------------------ 内部

    private User requireActor(@Nullable UUID actorId) {
        if (actorId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证，无法发起模拟");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "操作者不存在: " + actorId));
        boolean isAdmin = roleCodesOf(actor.getId()).stream().anyMatch(code -> "admin".equalsIgnoreCase(code));
        if (!isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有管理员可以发起或结束模拟视角");
        }
        return actor;
    }

    private List<String> roleCodesOf(UUID userId) {
        List<UserRole> links = userRoleRepository.findByUserId(userId);
        if (links.isEmpty()) {
            return List.of();
        }
        List<UUID> roleIds = links.stream().map(UserRole::getRoleId).filter(Objects::nonNull).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.findAllById(roleIds).stream()
                .map(Role::getCode)
                .filter(Objects::nonNull)
                .map(code -> code.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String toAuthority(String roleCode) {
        return "ROLE_" + roleCode.toUpperCase(Locale.ROOT);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
