package com.vulnaudit.controller;

import com.vulnaudit.config.JwtProperties;
import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.ChangePasswordRequest;
import com.vulnaudit.dto.LoginRequest;
import com.vulnaudit.dto.LoginResponse;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.enums.LoginTheme;
import com.vulnaudit.exception.TooManyRequestsException;
import com.vulnaudit.security.JwtUtils;
import com.vulnaudit.security.RoleAuthority;
import com.vulnaudit.service.LoginAttemptService;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 认证控制器
 *
 * 功能说明：
 * - 处理用户登录请求
 * - 处理用户登出请求
 * - 处理密码修改请求
 * - 处理令牌刷新请求
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录、登出、密码修改等接口")
public class AuthController {

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final LoginAttemptService loginAttemptService;
    private final com.vulnaudit.service.SystemSettingService systemSettingService;
    private final com.vulnaudit.service.LoginLockoutService loginLockoutService;
    private final com.vulnaudit.service.TwoFactorService twoFactorService;
    /** remember-me 长效令牌（t124）：登录下发、刷新轮换、登出撤销 */
    private final com.vulnaudit.service.RememberMeService rememberMeService;

    /**
     * 登录页主题公开配置（t109）
     *
     * <p><b>无需认证</b>：登录页在用户登录之前就要拿到「管理员设置的缺省主题」与「全部可选主题」，
     * 因此该端点必须匿名可访问（见 {@code SecurityConfig} 的 permitAll 清单），
     * 且读取路径对配置缺失/脏值做过兜底（回落 pencil），不会把登录页拖挂。
     */
    @GetMapping("/login-theme")
    @Operation(summary = "登录页主题公开配置",
            description = "无需认证；返回 {defaultTheme, themes:[{code}]}。缺省值由管理员设置，缺失/非法回落 pencil")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功")
    })
    public ApiResponse<com.vulnaudit.dto.PublicLoginThemeResponse> getPublicLoginTheme() {
        return ApiResponse.success(systemSettingService.getPublicLoginTheme());
    }

    /**
     * 登录/刷新响应里的 {@code loginTheme}（t112 三态）
     *
     * <p>用户行 {@code login_theme} 为 NULL 时<b>原样返回 null</b>（= 跟随系统默认），
     * 不再回填管理员缺省值 —— 否则「跟随默认」会被固化成具体主题，管理员之后改缺省就跟随不上了。
     * 历史脏值（非法 code）仍回落默认主题，避免把脏值回吐给前端。
     */
    private static String loginThemeOf(User user) {
        if (user.getLoginTheme() == null) {
            return null;
        }
        return LoginTheme.fromCodeOrDefault(user.getLoginTheme()).getCode();
    }

    /** 是否跟随系统默认（与 {@link #loginThemeOf(User)} 严格互补） */
    private static boolean followDefaultOf(User user) {
        return user.getLoginTheme() == null;
    }

    /** 取真实客户端 IP：反向代理场景优先使用 X-Forwarded-For 首个地址 */    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回JWT令牌")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "用户名或密码错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "用户账号已被禁用")
    })
    public org.springframework.http.ResponseEntity<?> login(
            @Parameter(description = "登录请求", required = true)
            @Valid @RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse) {
        log.info("用户登录请求: username={}", request.getUsername());

        String clientIp = resolveClientIp(httpRequest);

        // t123 两级锁定：先 IP（429，不影响账户）→ 再账户（423，任何 IP 的正确密码也拒绝）
        if (loginLockoutService.isEnabled()) {
            long ipLock = loginLockoutService.ipLockRemainingSeconds(clientIp);
            if (ipLock > 0) {
                log.warn("登录被 IP 级锁定: ip={}, retryAfter={}s", clientIp, ipLock);
                throw new TooManyRequestsException("该 IP 登录失败次数过多，请 " + ipLock + " 秒后重试", ipLock);
            }
            User candidate = userService.findByUsername(request.getUsername()).orElse(null);
            if (candidate != null) {
                long accountLock = loginLockoutService.accountLockRemainingSeconds(candidate.getId());
                if (accountLock > 0) {
                    throw new com.vulnaudit.exception.AccountLockedException(
                            "账户因多次登录失败已被临时锁定，请通过验证码解锁或联系管理员",
                            accountLock,
                            loginLockoutService.unlockMethodsOf(candidate),
                            loginLockoutService.maskedTargetsOf(candidate),
                            "BRUTE_FORCE");
                }
            }
        } else {
            // 关闭新模型时回归旧的「用户名+IP」限流
            String attemptKey = LoginAttemptService.key(request.getUsername(), clientIp);
            if (loginAttemptService.isLocked(attemptKey)) {
                long retryAfter = loginAttemptService.retryAfterSeconds(attemptKey);
                log.warn("登录被限流: username={}, retryAfter={}s", request.getUsername(), retryAfter);
                throw new TooManyRequestsException("登录失败次数过多，请 " + retryAfter + " 秒后重试", retryAfter);
            }
        }

        User user;
        try {
            user = userService.authenticate(request.getUsername(), request.getPassword());
        } catch (RuntimeException ex) {
            if (loginLockoutService.isEnabled()) {
                User failedUser = userService.findByUsername(request.getUsername()).orElse(null);
                boolean justLocked = loginLockoutService.onLoginFailure(failedUser, request.getUsername(), clientIp);
                if (justLocked && failedUser != null) {
                    long accountLock = loginLockoutService.accountLockRemainingSeconds(failedUser.getId());
                    throw new com.vulnaudit.exception.AccountLockedException(
                            "账户因多次登录失败已被临时锁定，请通过验证码解锁或联系管理员",
                            Math.max(accountLock, 1),
                            loginLockoutService.unlockMethodsOf(failedUser),
                            loginLockoutService.maskedTargetsOf(failedUser),
                            "BRUTE_FORCE");
                }
            } else {
                loginAttemptService.onFailure(LoginAttemptService.key(request.getUsername(), clientIp));
            }
            throw ex;
        }
        if (loginLockoutService.isEnabled()) {
            loginLockoutService.onLoginSuccess(user);
        } else {
            loginAttemptService.onSuccess(LoginAttemptService.key(request.getUsername(), clientIp));
        }
        // t125：已开启双因子的用户不直接签发 JWT —— 先返回 202 + 一次性挑战 token
        if (twoFactorService.isAvailable() && user.isTwoFactorEnabled()) {
            String challengeToken = twoFactorService.registerChallenge(user.getId());
            log.info("登录需要二次验证: userId={}", user.getId());
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("twoFactorRequired", true);
            payload.put("challengeToken", challengeToken);
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(payload, "请输入身份验证器动态码"));
        }
        userService.updateLastLogin(user.getId());
        // t124：rememberMe=true 时额外下发独立长效 cookie（JWT 本身不延长）
        attachRememberMeCookie(httpResponse, user, Boolean.TRUE.equals(request.getRememberMe()),
                httpRequest.getHeader("User-Agent"), clientIp);
        return org.springframework.http.ResponseEntity.ok(ApiResponse.success(buildLoginResponse(user)));
    }

    /** 统一构建登录响应（t125：带上「是否必须立即设置 2FA」标记） */
    private LoginResponse buildLoginResponse(User user) {
        List<String> roles = userService.getUserRoleCodes(user.getId());
        String token = jwtUtils.generateToken(user.getUsername(),
                roles.stream()
                        .map(r -> new SimpleGrantedAuthority(RoleAuthority.of(r)))
                        .collect(Collectors.toList()));
        return LoginResponse.builder()
                .token(token)
                .tokenType(jwtProperties.getPrefix().trim())
                .expiresIn(jwtProperties.getExpiration())
                .userId(user.getId().toString())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .loginTheme(loginThemeOf(user))
                .followDefault(followDefaultOf(user))
                .mustSetupTwoFactor(twoFactorService.mustSetupTwoFactor(user))
                .build();
    }

    /**
     * 登录第二步：校验动态码/恢复码并签发 JWT（t125）
     *
     * <p>第一步（密码正确但已开启 2FA）返回 202 + {@code challengeToken}；本端点消费该 token（一次性、5 分钟）。
     * 失败计数达到上限时挑战作废（需重新登录）。
     */
    @PostMapping("/login/2fa")
    @Operation(summary = "登录二次验证", description = "携带挑战 token 与动态码（或恢复码）换取正式 JWT")
    public ApiResponse<LoginResponse> loginWithTwoFactor(
            @Valid @RequestBody TwoFactorLoginRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse) {
        UUID userId = twoFactorService.consumeChallenge(request.challengeToken());
        if (!twoFactorService.verifyLoginCode(userId, request.code())) {
            twoFactorService.recordChallengeFailure(request.challengeToken());
            throw new com.vulnaudit.exception.BusinessException(
                    com.vulnaudit.exception.ErrorCode.BAD_REQUEST, "动态码或恢复码不正确");
        }
        twoFactorService.invalidateChallenge(request.challengeToken());
        User user = userService.findById(userId)
                .orElseThrow(() -> new com.vulnaudit.exception.BusinessException(
                        com.vulnaudit.exception.ErrorCode.USER_NOT_FOUND));
        userService.updateLastLogin(user.getId());
        // t124：第二步也可勾选 rememberMe（第一步的勾选对 2FA 用户不生效，需在第二步确认）
        attachRememberMeCookie(httpResponse, user, Boolean.TRUE.equals(request.rememberMe()),
                httpRequest.getHeader("User-Agent"), resolveClientIp(httpRequest));
        log.info("二次验证通过并签发 token: userId={}", user.getId());
        return ApiResponse.success(buildLoginResponse(user));
    }

    /** 登录二次验证请求体 */
    public record TwoFactorLoginRequest(
            @jakarta.validation.constraints.NotBlank(message = "validation.twoFactor.challenge.required")
            String challengeToken,
            @jakarta.validation.constraints.NotBlank(message = "validation.twoFactor.code.required")
            String code,
            /** 是否记住登录（t124）：true 时第二步成功后下发 remember-me cookie */
            Boolean rememberMe) {
    }
    /**
     * 用户登出
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户登出，清除服务端会话（JWT无状态，客户端清除令牌即可）")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登出成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权")
    })
    public ApiResponse<Void> logout(jakarta.servlet.http.HttpServletRequest httpRequest,
                                    jakarta.servlet.http.HttpServletResponse httpResponse) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            log.info("用户登出: username={}", authentication.getName());
        }
        // t124：撤销当前 remember-me 令牌并清 cookie（JWT 无状态，客户端丢弃即可）
        String raw = readRememberMeCookie(httpRequest);
        if (raw != null) {
            rememberMeService.revoke(raw);
        }
        clearRememberMeCookie(httpResponse);
        return ApiResponse.success(null);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息（含登录页主题偏好 loginTheme）")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权")
    })
    public ResponseEntity<ApiResponse<User>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            log.warn("未找到认证信息");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }

        String username = authentication.getName();
        User user = userService.findByUsername(username)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "用户不存在"));
        }

        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    @Operation(summary = "修改密码", description = "用户修改自己的密码")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "密码修改成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "旧密码错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权")
    })
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Parameter(description = "修改密码请求", required = true)
            @Valid @RequestBody ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            log.warn("未找到认证信息");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }

        String username = authentication.getName();
        User user = userService.findByUsername(username)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "用户不存在"));
        }

        boolean success = userService.changePassword(user.getId(), request.getOldPassword(), request.getNewPassword());

        if (success) {
            log.info("密码修改成功: username={}", username);
            return ResponseEntity.ok(ApiResponse.success(null));
        } else {
            log.warn("密码修改失败: username={}", username);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "密码修改失败"));
        }
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用当前有效令牌刷新获取新令牌")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "刷新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权")
    })
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            jakarta.servlet.http.HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
        if (!authenticated) {
            // t124：JWT 过期但 remember-me cookie 有效 → 轮换并重签 JWT（旧令牌立即失效）
            String raw = readRememberMeCookie(httpRequest);
            if (raw == null || !rememberMeService.isEnabled()) {
                log.warn("刷新令牌失败：无有效认证信息且无 remember-me cookie");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "未授权"));
            }
            String rotated;
            try {
                rotated = rememberMeService.rotate(raw, httpRequest.getHeader("User-Agent"),
                        resolveClientIp(httpRequest));
            } catch (com.vulnaudit.exception.BusinessException e) {
                clearRememberMeCookie(httpResponse);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, e.getMessage()));
            }
            java.util.UUID rotatedUser = rememberMeService.userIdOf(rotated);
            User cookieUser = rotatedUser == null ? null : userService.findById(rotatedUser).orElse(null);
            if (cookieUser == null || !cookieUser.isActive()) {
                rememberMeService.revoke(rotated);
                clearRememberMeCookie(httpResponse);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "账号不可用，请重新登录"));
            }
            writeRememberMeCookie(httpResponse, rotated);
            log.info("remember-me 刷新成功: username={}", cookieUser.getUsername());
            return ResponseEntity.ok(ApiResponse.success(buildLoginResponse(cookieUser)));
        }

        String username = authentication.getName();
        User user = userService.findByUsername(username)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "用户不存在"));
        }

        log.info("令牌刷新成功: username={}", username);
        return ResponseEntity.ok(ApiResponse.success(buildLoginResponse(user)));
    }

    // ------------------------------------------------------------------ remember-me（t124）

    /** 登录成功后按需下发 remember-me cookie（enabled=false 时不下发） */
    private void attachRememberMeCookie(jakarta.servlet.http.HttpServletResponse response,
                                        User user,
                                        boolean rememberMe,
                                        String userAgent,
                                        String ip) {
        if (!rememberMe || !rememberMeService.isEnabled()) {
            return;
        }
        String raw = rememberMeService.issue(user.getId(), userAgent, ip);
        writeRememberMeCookie(response, raw);
        log.info("已下发 remember-me 令牌: userId={}", user.getId());
    }

    private void writeRememberMeCookie(jakarta.servlet.http.HttpServletResponse response, String rawToken) {
        var cookie = org.springframework.http.ResponseCookie
                .from(rememberMeService.cookieName(), rawToken)
                .httpOnly(true)
                .secure(rememberMeService.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(java.time.Duration.ofSeconds(rememberMeService.cookieMaxAgeSeconds()))
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRememberMeCookie(jakarta.servlet.http.HttpServletResponse response) {
        var cookie = org.springframework.http.ResponseCookie
                .from(rememberMeService.cookieName(), "")
                .httpOnly(true)
                .secure(rememberMeService.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(java.time.Duration.ZERO)
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readRememberMeCookie(jakarta.servlet.http.HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (rememberMeService.cookieName().equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }

}
