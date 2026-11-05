package com.vulnaudit.service.impl;

import com.vulnaudit.dto.ApproveRegistrationResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.RegisterRequest;
import com.vulnaudit.dto.RegistrationRequestVO;
import com.vulnaudit.entity.RegistrationRequest;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.entity.enums.RegistrationStatus;
import com.vulnaudit.entity.enums.VerificationChannel;
import com.vulnaudit.entity.enums.VerificationPurpose;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.RegistrationRequestRepository;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.SsoBindingRepository;
import com.vulnaudit.repository.SsoPendingBindingRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.UserRoleRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.RegistrationService;
import com.vulnaudit.service.VerificationCodeService;
import com.vulnaudit.util.Paging;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 自助注册与审批实现（t102）
 *
 * <h3>为什么注册时就建一条 pending_approval 用户</h3>
 * <ul>
 *   <li>用户名 / 手机号 / 邮箱的唯一性直接由 {@code users} 的唯一约束兜住（并发下也不会重复）；</li>
 *   <li>「待审批用户尝试登录 → 403」有真实主体可判定（要求 7），无需登录链路反查申请表；</li>
 *   <li>审批通过只需把占位用户置 active 并补齐验证标记，不涉及第二套建号逻辑。</li>
 * </ul>
 * 占位用户<b>不可登录</b>：{@code UserServiceImpl.authenticate} 在密码校验前先按状态拒绝
 * （PENDING_APPROVAL / REJECTED → 403，且不签发 token）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    /** 用户名：字母/数字/下划线，3–50 */
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,50}$");

    /** 未提供邮箱时占位用户的邮箱后缀（users.email 为 NOT NULL，且必须全局唯一） */
    private static final String NO_EMAIL_SUFFIX = "@no-email.local";

    /** 审批通过后默认分配的角色（存在才分配；不存在则不分配，登录后仅有默认 ROLE_USER） */
    private static final String DEFAULT_ROLE_CODE = "vulnerability_engineer";

    private final RegistrationRequestRepository registrationRequestRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final VerificationCodeService verificationCodeService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final SsoBindingRepository ssoBindingRepository;
    private final SsoPendingBindingRepository ssoPendingBindingRepository;

    /** 用于「占位用户是否有关联任务/资产」的轻量计数查询（t111） */
    @PersistenceContext
    private EntityManager entityManager;

    // ------------------------------------------------------------------ 注册

    @Override
    @Transactional
    public RegistrationRequestVO register(RegisterRequest request) {
        String username = requireTrimmed(request.username(), "用户名不能为空");
        if (!USERNAME.matcher(username).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名需为 3–50 位字母、数字或下划线: " + username);
        }
        String phone = Objects.requireNonNull(verificationCodeService.normalize(VerificationChannel.SMS, request.phone()));
        String email = StringUtils.hasText(request.email())
                ? verificationCodeService.normalize(VerificationChannel.EMAIL, request.email())
                : null;

        // 1) 唯一性（先查再写，唯一约束兜底并发）
        assertUsernameAvailable(username);
        assertPhoneAvailable(phone);
        if (email != null) {
            assertEmailAvailable(email);
        }

        // 2) 验证码：手机必须通过；填了邮箱则邮箱也必须通过（校验成功即消费，失败抛 400 → 事务回滚）
        verificationCodeService.verifyAndConsume(phone, VerificationChannel.SMS,
                VerificationPurpose.REGISTER, request.code());
        if (email != null) {
            if (!StringUtils.hasText(request.emailCode())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "已填写邮箱，必须同时通过邮箱验证码校验");
            }
            verificationCodeService.verifyAndConsume(email, VerificationChannel.EMAIL,
                    VerificationPurpose.REGISTER, request.emailCode());
        }

        // 3) 占位用户（不可登录）
        String storedEmail = email != null ? email : username + NO_EMAIL_SUFFIX;
        User placeholder = Objects.requireNonNull(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .email(storedEmail)
                .phone(phone)
                .fullName(username)
                .status(User.STATUS_PENDING_APPROVAL)
                .phoneVerified(true)
                .emailVerified(email != null)
                .build());
        userRepository.save(placeholder);

        // 4) 注册申请
        RegistrationRequest entity = RegistrationRequest.builder()
                .username(username)
                .passwordHash(placeholder.getPasswordHash())
                .phone(phone)
                .email(email)
                .phoneVerified(true)
                .emailVerified(email != null)
                .status(RegistrationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        registrationRequestRepository.save(entity);

        // 5) 审计：审计表 user_id 为 NOT NULL，这里记「申请人本人」（占位用户）而不是 null；
        //    动作名 REGISTER_SUBMIT 明确区分「提交申请」与「管理员审批」两类事件。
        auditLogService.log(placeholder.getId(), "REGISTER_SUBMIT", "registration-request", entity.getId(),
                "注册申请提交: username=" + username
                        + ", phone=" + maskPhone(phone)
                        + ", email=" + (email == null ? "(未提供)" : email)
                        + ", status=PENDING_APPROVAL");

        log.info("自助注册申请已提交: username={}, requestId={}", username, entity.getId());
        return RegistrationRequestVO.from(entity);
    }

    // ------------------------------------------------------------------ 查询 / 审批

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RegistrationRequestVO> list(@Nullable String status, int page, int size) {
        PageRequest pageable = PageRequest.of(Paging.page(page), Paging.size(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RegistrationRequest> result;
        if (StringUtils.hasText(status)) {
            RegistrationStatus parsed = parseStatus(status);
            result = registrationRequestRepository.findByStatus(parsed, pageable);
        } else {
            result = registrationRequestRepository.findAll(pageable);
        }
        return PageResponse.<RegistrationRequestVO>builder()
                .content(result.getContent().stream().map(RegistrationRequestVO::from).toList())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(result.getNumber())
                .pageSize(result.getSize())
                .build();
    }

    @Override
    @Transactional
    public ApproveRegistrationResponse approve(UUID requestId, UUID reviewerId, List<String> roleCodes) {
        RegistrationRequest request = requirePending(requestId);

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "注册申请对应的占位用户不存在: " + request.getUsername()));

        user.setStatus(User.STATUS_ACTIVE);
        if (StringUtils.hasText(request.getPhone())) {
            // SSO 申请没有手机号（t104）→ 不能把已有手机号置空
            user.setPhone(request.getPhone());
            user.setPhoneVerified(true);
        }
        if (StringUtils.hasText(request.getEmail())) {
            user.setEmail(request.getEmail());
            user.setEmailVerified(request.isEmailVerified());
        }
        userRepository.save(user);
        // t111：角色必须显式指定；空数组 = 建号但不授角色（不再隐式给 vulnerability_engineer）
        assignRoles(user.getId(), roleCodes, reviewerId);

        // t104：SSO 首次登录的「待审批绑定」在审批通过时转为正式绑定
        activateSsoBindings(request, user, reviewerId);

        request.setStatus(RegistrationStatus.APPROVED);
        request.setReviewerId(reviewerId);
        request.setReviewedAt(LocalDateTime.now());
        registrationRequestRepository.save(request);

        auditLogService.log(reviewerId != null ? reviewerId : user.getId(),
                "REGISTRATION_APPROVE", "registration-request", request.getId(),
                "注册申请通过: username=" + request.getUsername()
                        + ", phone=" + maskPhone(request.getPhone())
                        + ", userId=" + user.getId());

        log.info("注册申请已通过: username={}, userId={}", request.getUsername(), user.getId());
        return new ApproveRegistrationResponse(RegistrationRequestVO.from(request),
                user.getId(), user.getUsername(), true);
    }

    @Override
    @Transactional
    public RegistrationRequestVO reject(UUID requestId, UUID reviewerId, String note) {
        if (!StringUtils.hasText(note)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "拒绝原因不能为空");
        }
        RegistrationRequest request = requirePending(requestId);

        User target = userRepository.findByUsername(request.getUsername()).orElse(null);
        String release = "无占位用户";
        if (target != null) {
            // t111：拒绝即释放唯一键，否则同一手机号/用户名无法再次申请（真实可用性缺陷）
            release = releaseUniqueKeys(target, reviewerId);
        }

        request.setStatus(RegistrationStatus.REJECTED);
        request.setReviewerId(reviewerId);
        request.setReviewNote(note.trim());
        request.setReviewedAt(LocalDateTime.now());
        registrationRequestRepository.save(request);

        auditLogService.log(reviewerId != null ? reviewerId : (target != null ? target.getId() : null),
                "REGISTRATION_REJECT", "registration-request", request.getId(),
                "注册申请被拒: username=" + request.getUsername()
                        + ", phone=" + maskPhone(request.getPhone())
                        + ", note=" + note.trim()
                        + ", release=" + release);

        log.info("注册申请已被拒绝: username={}, release={}", request.getUsername(), release);
        return RegistrationRequestVO.from(request);
    }

    @Override
    @Transactional
    public void purge(UUID requestId, UUID operatorId) {
        RegistrationRequest request = registrationRequestRepository.findByIdForUpdate(requestId).orElse(null);
        if (request == null) {
            log.info("清理注册申请：记录不存在，按幂等处理 requestId={}", requestId);
            return; // 上层回 204（幂等）
        }
        if (request.getStatus() == RegistrationStatus.APPROVED) {
            // 已通过 = 已是正式账号；删除会让同名/同手机可被重复注册，语义上必须拒绝
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该申请已通过审批并生成正式账号，不能清理（可停用账号代替）");
        }

        String username = request.getUsername();
        ssoPendingBindingRepository.findByRegistrationRequestId(requestId).forEach(binding -> {
            if (binding.isPending()) {
                ssoPendingBindingRepository.delete(binding);
            }
        });
        registrationRequestRepository.delete(request);

        User target = userRepository.findByUsername(username).orElse(null);
        String release = "无占位用户";
        if (target != null) {
            release = releaseUniqueKeys(target, operatorId);
        }

        auditLogService.log(operatorId, "REGISTRATION_PURGE", "registration-request", requestId,
                "清理注册申请: requestId=" + requestId
                        + ", username=" + username
                        + ", status=" + request.getStatus()
                        + ", release=" + release);

        log.info("注册申请已清理: requestId={}, username={}, release={}", requestId, username, release);
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 释放占位用户占用的唯一键（t111）
     *
     * <p>策略（先删后归档）：
     * <ol>
     *   <li><b>直接删除</b>：占位用户「从未登录（last_login 为空）」且「无角色 / 无任务 / 无资产」时删除整行
     *       —— 这是注册占位用户的常态（审批前不能登录、不会被分配角色/任务/资产）；</li>
     *   <li><b>归档释放</b>：存在关联数据时不能删（会破坏外键语义与统计），改为把 username/phone 改成
     *       {@code rejected_<id8>_原值} / {@code rej_<id8>_尾号} 的归档值，并置 {@code rejected} 状态，
     *       一样能释放唯一键。</li>
     * </ol>
     *
     * @return 采取的策略说明（写入审计）
     */
    private String releaseUniqueKeys(User user, UUID operatorId) {
        boolean related = !userRoleRepository.findByUserId(user.getId()).isEmpty()
                || countTasks(user.getId()) > 0
                || countAssets(user.getId()) > 0;
        boolean neverLoggedIn = user.getLastLogin() == null;

        if (neverLoggedIn && !related) {
            UUID userId = user.getId();
            if (ssoBindingRepository.findByUserId(userId).isEmpty()) {
                userRepository.delete(user);
                return "deleted(占位用户从未登录且无关联数据)";
            }
        }

        String suffix = user.getId().toString().substring(0, 8);
        String archivedUsername = truncate("rejected_" + suffix + "_" + user.getUsername(), 50);
        user.setUsername(archivedUsername);
        if (StringUtils.hasText(user.getPhone())) {
            String phone = user.getPhone();
            String tail = phone.length() <= 4 ? phone : phone.substring(phone.length() - 4);
            user.setPhone(truncate("rej_" + suffix + "_" + tail, 20));
        }
        user.setStatus(User.STATUS_REJECTED);
        userRepository.save(user);
        return "archived(username=" + archivedUsername + ", 有关联数据或曾登录)";
    }

    private long countTasks(UUID userId) {
        return entityManager.createQuery(
                        "select count(t) from Task t where t.assignedTo = :uid or t.assignedBy = :uid or t.reviewedBy = :uid",
                        Long.class)
                .setParameter("uid", userId)
                .getSingleResult();
    }

    private long countAssets(UUID userId) {
        return entityManager.createQuery(
                        "select count(a) from Asset a where a.ownerId = :uid", Long.class)
                .setParameter("uid", userId)
                .getSingleResult();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 审批通过时把 SSO 待审批绑定转为正式绑定（t104）
     *
     * <p>只在 {@code registration_requests.source=SSO} 且存在 PENDING 记录时生效；
     * 已存在同一 binding 时跳过（幂等），并写 {@code SSO_BIND} 审计。
     */
    private void activateSsoBindings(RegistrationRequest request, User user, UUID reviewerId) {
        List<com.vulnaudit.entity.SsoPendingBinding> pendings =
                ssoPendingBindingRepository.findByRegistrationRequestIdAndStatus(request.getId(), "PENDING");
        if (pendings.isEmpty()) {
            return;
        }
        for (com.vulnaudit.entity.SsoPendingBinding pending : pendings) {
            if (ssoBindingRepository
                    .findByProviderCodeAndExternalId(pending.getProviderCode(), pending.getExternalId())
                    .isEmpty()) {
                ssoBindingRepository.save(com.vulnaudit.entity.SsoBinding.builder()
                        .userId(user.getId())
                        .providerCode(pending.getProviderCode())
                        .externalId(pending.getExternalId())
                        .displayName(pending.getDisplayName())
                        .boundAt(LocalDateTime.now())
                        .build());
            }
            pending.setStatus("ACTIVATED");
            pending.setActivatedAt(LocalDateTime.now());
            ssoPendingBindingRepository.save(pending);

            auditLogService.log(reviewerId != null ? reviewerId : user.getId(),
                    "SSO_BIND", "user", user.getId(),
                    "审批通过并绑定 SSO: provider=" + pending.getProviderCode()
                            + ", externalId=" + pending.getExternalId()
                            + ", requestId=" + request.getId());
        }
    }

    private void assertUsernameAvailable(String username) {
        if (userRepository.existsByUsername(username)
                || registrationRequestRepository.existsByUsernameAndStatus(username, RegistrationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "用户名已存在: " + username);
        }
    }

    private void assertPhoneAvailable(String phone) {
        if (userRepository.existsByPhone(phone)
                || registrationRequestRepository.existsByPhoneAndStatus(phone, RegistrationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.CONFLICT, "手机号已被注册: " + maskPhone(phone));
        }
    }

    private void assertEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)
                || registrationRequestRepository.existsByEmailAndStatus(email, RegistrationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "邮箱已被注册: " + email);
        }
    }

    private RegistrationRequest requirePending(UUID requestId) {
        // t111：加行级写锁，令 approve / reject / purge 并发时串行化，后到者看到已变更状态 → 409
        RegistrationRequest request = registrationRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "注册申请不存在: " + requestId));
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该注册申请已处理（当前状态: " + request.getStatus() + "）");
        }
        return request;
    }

    /**
     * 按审批人显式指定的角色码授权（t111 口径，用户拍板）
     *
     * <p><b>空数组 = 建号但不授角色</b>（登录后只有默认 ROLE_USER，无任何业务权限）；
     * **不再隐式补 {@code vulnerability_engineer}** —— 授权必须由审批人显式选择，
     * 避免"批了账号等于批了工程师权限"的越权默认。
     *
     * @param userId     目标用户
     * @param roleCodes  角色码列表（可空 = 不授角色）
     * @param reviewerId 审批人（写入 user_roles.assigned_by）
     */
    private void assignRoles(UUID userId, List<String> roleCodes, UUID reviewerId) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            log.info("审批通过但未指定角色，该账号登录后仅有默认 ROLE_USER: userId={}", userId);
            return;
        }
        if (!userRoleRepository.findByUserId(userId).isEmpty()) {
            return;
        }
        for (String code : roleCodes.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList()) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "角色不存在: " + code));
            userRoleRepository.save(Objects.requireNonNull(UserRole.builder()
                    .userId(userId)
                    .roleId(role.getId())
                    .assignedBy(reviewerId)
                    .assignedAt(LocalDateTime.now())
                    .build()));
        }
    }

    private RegistrationStatus parseStatus(String raw) {
        try {
            return RegistrationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "无效的申请状态: " + raw + "（支持: PENDING, APPROVED, REJECTED）");
        }
    }

    private String requireTrimmed(String raw, String message) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        return raw.trim();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
