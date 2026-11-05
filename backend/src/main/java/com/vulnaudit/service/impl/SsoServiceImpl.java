package com.vulnaudit.service.impl;

import com.vulnaudit.config.JwtProperties;
import com.vulnaudit.dto.LoginResponse;
import com.vulnaudit.dto.SsoAuthorizeResponse;
import com.vulnaudit.dto.SsoBindingVO;
import com.vulnaudit.dto.SsoCallbackResponse;
import com.vulnaudit.dto.SsoProviderOption;
import com.vulnaudit.entity.RegistrationRequest;
import com.vulnaudit.entity.SsoBinding;
import com.vulnaudit.entity.SsoPendingBinding;
import com.vulnaudit.entity.SsoProvider;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.enums.LoginTheme;
import com.vulnaudit.entity.enums.RegistrationStatus;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.RegistrationRequestRepository;
import com.vulnaudit.repository.SsoBindingRepository;
import com.vulnaudit.repository.SsoPendingBindingRepository;
import com.vulnaudit.repository.SsoProviderRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.security.JwtUtils;
import com.vulnaudit.security.RoleAuthority;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.SsoService;
import com.vulnaudit.service.UserService;
import com.vulnaudit.service.sso.SsoClient;
import com.vulnaudit.service.sso.SsoClientFactory;
import com.vulnaudit.service.sso.SsoStateStore;
import com.vulnaudit.service.sso.SsoUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SSO 登录与绑定实现（t104）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoServiceImpl implements SsoService {

    /** 陌生身份首次登录时，注册申请里落的来源标记 */
    private static final String SOURCE_SSO = "SSO";

    private final SsoProviderRepository ssoProviderRepository;
    private final SsoBindingRepository ssoBindingRepository;
    private final SsoPendingBindingRepository ssoPendingBindingRepository;
    private final RegistrationRequestRepository registrationRequestRepository;
    private final UserRepository userRepository;
    private final SsoClientFactory ssoClientFactory;
    private final SsoStateStore ssoStateStore;
    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    // ------------------------------------------------------------------ 提供商

    @Override
    @Transactional(readOnly = true)
    public List<SsoProviderOption> publicProviders() {
        return ssoProviderRepository.findAllByOrderByCodeAsc().stream()
                .filter(SsoProvider::isUsable)
                .map(p -> new SsoProviderOption(p.getCode(), p.getName(), p.getIcon()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SsoProvider> allProviders() {
        return ssoProviderRepository.findAllByOrderByCodeAsc();
    }

    // ------------------------------------------------------------------ 授权 / 回调

    @Override
    @Transactional(readOnly = true)
    public SsoAuthorizeResponse authorizeUrl(String providerCode, String redirectUri) {
        SsoProvider provider = requireUsable(providerCode);
        String state = ssoStateStore.issue(provider.getCode(), redirectUri, null);
        SsoClient client = requireClient(provider);
        return new SsoAuthorizeResponse(client.authorizeUrl(state, redirectUri), state);
    }

    @Override
    @Transactional
    public SsoCallbackResponse callback(String providerCode, String code, String state, String clientIp) {
        SsoProvider provider = requireUsable(providerCode);
        SsoStateStore.StateEntry entry = ssoStateStore.consume(state, provider.getCode());
        if (entry == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "登录状态已失效，请重新发起第三方登录");
        }
        SsoClient client = requireClient(provider);
        SsoUserInfo info = client.exchange(code);

        // ① 绑定流程（state 携带已登录用户）
        if (entry.bindingUserId() != null) {
            return bindExisting(entry.bindingUserId(), provider, info);
        }

        // ② 已绑定 → 签发 token（账号必须 active）
        SsoBinding binding = ssoBindingRepository
                .findByProviderCodeAndExternalId(provider.getCode(), info.externalId())
                .orElse(null);
        if (binding != null) {
            User user = userRepository.findById(binding.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "error.auth.rejected"));
            assertLoginable(user);
            LoginResponse login = issueToken(user);
            log.info("SSO 登录成功: provider={}, userId={}", provider.getCode(), user.getId());
            auditLogService.log(user.getId(), "SSO_LOGIN", "user", user.getId(),
                    "SSO 登录: provider=" + provider.getCode() + ", externalId=" + info.externalId());
            return new SsoCallbackResponse("LOGIN", login, provider.getCode());
        }

        // ③ 陌生身份 → 注册申请 + 待审批绑定（不签发 token）
        SsoPendingBinding pending = ssoPendingBindingRepository
                .findByProviderCodeAndExternalId(provider.getCode(), info.externalId())
                .orElse(null);
        if (pending != null && pending.isPending()) {
            log.info("SSO 首次登录重复回调，返回待审批: provider={}, externalId={}",
                    provider.getCode(), info.externalId());
            return new SsoCallbackResponse("PENDING_APPROVAL", null, provider.getCode());
        }

        createPendingRegistration(provider, info, clientIp);
        return new SsoCallbackResponse("PENDING_APPROVAL", null, provider.getCode());
    }

    // ------------------------------------------------------------------ 绑定

    @Override
    @Transactional(readOnly = true)
    public List<SsoBindingVO> bindings(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证，无法读取 SSO 绑定");
        }
        return ssoBindingRepository.findByUserId(userId).stream().map(SsoBindingVO::from).toList();
    }

    @Override
    public SsoAuthorizeResponse startBind(UUID userId, String providerCode, String redirectUri) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证，无法发起绑定");
        }
        SsoProvider provider = requireUsable(providerCode);
        if (ssoBindingRepository.findByUserIdAndProviderCode(userId, provider.getCode()).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该账号已绑定 " + provider.getName() + "，请先解绑");
        }
        String state = ssoStateStore.issue(provider.getCode(), redirectUri, userId);
        SsoClient client = requireClient(provider);
        return new SsoAuthorizeResponse(client.authorizeUrl(state, redirectUri), state);
    }

    @Override
    @Transactional
    public void unbind(UUID userId, String providerCode, String password) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证，无法解绑");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!StringUtils.hasText(password) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前密码不正确，无法解绑");
        }
        SsoBinding binding = ssoBindingRepository.findByUserIdAndProviderCode(userId, normalize(providerCode))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "该账号未绑定此登录方式"));
        ssoBindingRepository.delete(binding);
        auditLogService.log(userId, "SSO_UNBIND", "user", userId,
                "解绑 SSO: provider=" + binding.getProviderCode() + ", externalId=" + binding.getExternalId());
        log.info("SSO 解绑成功: userId={}, provider={}", userId, binding.getProviderCode());
    }

    // ------------------------------------------------------------------ 管理员

    @Override
    @Transactional
    public SsoProvider toggle(String providerCode, boolean enabled, UUID adminId) {
        SsoProvider provider = ssoProviderRepository.findByCode(normalize(providerCode))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "不支持的登录方式: " + providerCode));
        provider.setEnabled(enabled);
        ssoProviderRepository.save(provider);
        auditLogService.log(adminId, "UPDATE_SSO_PROVIDER", "sso_provider", provider.getId(),
                "SSO 开关: provider=" + provider.getCode()
                        + ", enabled=" + enabled
                        + ", configured=" + provider.isConfigured());
        log.info("SSO 提供商开关更新: provider={}, enabled={}, configured={}",
                provider.getCode(), enabled, provider.isConfigured());
        return provider;
    }

    // ------------------------------------------------------------------ 内部

    private SsoProvider requireUsable(String providerCode) {
        String code = normalize(providerCode);
        SsoProvider provider = ssoProviderRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "不支持的登录方式: " + providerCode));
        if (!provider.isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该登录方式未开放: " + provider.getCode());
        }
        if (!provider.isConfigured()) {
            // 配置缺失属于「服务端尚未就绪」，不是用户输入错误
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "该登录方式尚未完成配置: " + provider.getCode());
        }
        return provider;
    }

    private SsoClient requireClient(SsoProvider provider) {
        SsoClient client = ssoClientFactory.forProvider(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的登录方式: " + provider.getCode());
        }
        return client;
    }

    private void assertLoginable(User user) {
        if (user.isPendingApproval()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "error.auth.pending_approval");
        }
        if (user.isRejected()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "error.auth.rejected");
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被停用，请联系管理员");
        }
    }

    private LoginResponse issueToken(User user) {
        List<String> roles = userService.getUserRoleCodes(user.getId());
        String token = jwtUtils.generateToken(user.getUsername(), roles.stream()
                .map(RoleAuthority::of)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList()));
        return LoginResponse.builder()
                .token(token)
                .tokenType(jwtProperties.getPrefix().trim())
                .expiresIn(jwtProperties.getExpiration())
                .userId(user.getId().toString())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .loginTheme(LoginTheme.fromCodeOrDefault(user.getLoginTheme()).getCode())
                .build();
    }

    private SsoCallbackResponse bindExisting(UUID userId, SsoProvider provider, SsoUserInfo info) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (ssoBindingRepository.findByUserIdAndProviderCode(userId, provider.getCode()).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该账号已绑定 " + provider.getName() + "，请先解绑");
        }
        SsoBinding taken = ssoBindingRepository
                .findByProviderCodeAndExternalId(provider.getCode(), info.externalId()).orElse(null);
        if (taken != null) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该 " + provider.getName() + " 账号已被其他本地用户绑定");
        }
        ssoBindingRepository.save(SsoBinding.builder()
                .userId(user.getId())
                .providerCode(provider.getCode())
                .externalId(info.externalId())
                .displayName(info.displayName())
                .boundAt(LocalDateTime.now())
                .build());
        auditLogService.log(user.getId(), "SSO_BIND", "user", user.getId(),
                "绑定 SSO: provider=" + provider.getCode() + ", externalId=" + info.externalId());
        log.info("SSO 绑定成功: userId={}, provider={}", user.getId(), provider.getCode());
        return new SsoCallbackResponse("BOUND", null, provider.getCode());
    }

    /**
     * 陌生身份：建注册申请（source=SSO）+ 待审批绑定 + pending_approval 占位用户（不可登录）
     */
    private void createPendingRegistration(SsoProvider provider, SsoUserInfo info, String clientIp) {
        String username = uniqueUsername(provider, info);
        String randomPassword = randomPassword();
        String email = StringUtils.hasText(info.email()) ? info.email().trim().toLowerCase(Locale.ROOT) : null;
        // users.email 为 NOT NULL 且唯一：没有真实邮箱时用占位地址（与 t102 同口径）
        String storedEmail = email != null ? email : username + "@no-email.local";

        User placeholder = userRepository.save(java.util.Objects.requireNonNull(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(randomPassword))
                .email(storedEmail)
                .fullName(StringUtils.hasText(info.displayName()) ? info.displayName() : username)
                .status(User.STATUS_PENDING_APPROVAL)
                .emailVerified(email != null)
                .build()));

        RegistrationRequest request = registrationRequestRepository.save(RegistrationRequest.builder()
                .username(username)
                .passwordHash(placeholder.getPasswordHash())
                .phone(null)
                .email(email)
                .phoneVerified(false)
                .emailVerified(email != null)
                .status(RegistrationStatus.PENDING)
                .source(SOURCE_SSO)
                .providerCode(provider.getCode())
                .externalId(info.externalId())
                .displayName(info.displayName())
                .createdAt(LocalDateTime.now())
                .build());

        ssoPendingBindingRepository.save(SsoPendingBinding.builder()
                .registrationRequestId(request.getId())
                .providerCode(provider.getCode())
                .externalId(info.externalId())
                .email(email)
                .displayName(info.displayName())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build());

        auditLogService.log(placeholder.getId(), "SSO_REGISTER_SUBMIT", "registration-request", request.getId(),
                "SSO 首次登录待审批: provider=" + provider.getCode()
                        + ", externalId=" + info.externalId()
                        + ", username=" + username
                        + ", email=" + (email == null ? "(未提供)" : email)
                        + ", ip=" + (clientIp == null ? "-" : clientIp));

        log.info("SSO 首次登录已提交审批: provider={}, externalId={}, username={}",
                provider.getCode(), info.externalId(), username);
    }

    /**
     * 由第三方身份派生一个可用用户名：优先邮箱前缀，其次 {@code sso_<provider>}；
     * 与 users / registration_requests 去重后追加序号。
     */
    private String uniqueUsername(SsoProvider provider, SsoUserInfo info) {
        String base;
        if (StringUtils.hasText(info.email()) && info.email().contains("@")) {
            base = info.email().substring(0, info.email().indexOf('@'));
        } else {
            base = "sso_" + provider.getCode();
        }
        base = base.replaceAll("[^A-Za-z0-9_]", "_");
        if (base.length() < 3) {
            base = "sso_" + base;
        }
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)
                || registrationRequestRepository.existsByUsernameAndStatus(candidate, RegistrationStatus.PENDING)) {
            suffix++;
            candidate = base + "_" + suffix;
        }
        return candidate;
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * SSO 用户初始随机密码（32 位字母数字）
     *
     * <p>注意 BCrypt 对密码字节数上限 72：不能用「两个 UUID 拼接」这类超长串，否则
     * {@code passwordEncoder.encode} 直接抛 IllegalArgumentException → 500。
     * 该密码用户不知道，登录走 SSO；需要本地密码时用「忘记密码」设置。
     */
    private String randomPassword() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(alphabet.charAt(java.util.concurrent.ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
