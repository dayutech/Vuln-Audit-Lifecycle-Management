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
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.UserRoleRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.RegistrationService;
import com.vulnaudit.service.VerificationCodeService;
import com.vulnaudit.util.Paging;
import jakarta.annotation.Nullable;
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
    public ApproveRegistrationResponse approve(UUID requestId, UUID reviewerId) {
        RegistrationRequest request = requirePending(requestId);

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "注册申请对应的占位用户不存在: " + request.getUsername()));

        user.setStatus(User.STATUS_ACTIVE);
        user.setPhone(request.getPhone());
        user.setPhoneVerified(true);
        if (StringUtils.hasText(request.getEmail())) {
            user.setEmail(request.getEmail());
            user.setEmailVerified(request.isEmailVerified());
        }
        userRepository.save(user);
        assignDefaultRole(user.getId(), reviewerId);

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
        if (target != null) {
            target.setStatus(User.STATUS_REJECTED);
            userRepository.save(target);
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
                        + ", note=" + note.trim());

        log.info("注册申请已被拒绝: username={}", request.getUsername());
        return RegistrationRequestVO.from(request);
    }

    // ------------------------------------------------------------------ 内部

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
        RegistrationRequest request = registrationRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "注册申请不存在: " + requestId));
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该注册申请已处理（当前状态: " + request.getStatus() + "）");
        }
        return request;
    }

    private void assignDefaultRole(UUID userId, UUID reviewerId) {
        Role role = roleRepository.findByCode(DEFAULT_ROLE_CODE).orElse(null);
        if (role == null) {
            log.warn("默认角色 {} 不存在，审批通过的用户将不分配角色（仅 ROLE_USER）", DEFAULT_ROLE_CODE);
            return;
        }
        if (!userRoleRepository.findByUserId(userId).isEmpty()) {
            return;
        }
        userRoleRepository.save(Objects.requireNonNull(UserRole.builder()
                .userId(userId)
                .roleId(role.getId())
                .assignedBy(reviewerId)
                .assignedAt(LocalDateTime.now())
                .build()));
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
