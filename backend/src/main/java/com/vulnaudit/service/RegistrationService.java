package com.vulnaudit.service;

import com.vulnaudit.dto.ApproveRegistrationResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.RegisterRequest;
import com.vulnaudit.dto.RegistrationRequestVO;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 自助注册与审批服务（t102）
 *
 * <p>流程：注册（验证码校验通过）→ 落 PENDING 申请 + pending_approval 占位用户（不可登录）
 * → 管理员 approve（转 active + 手机/邮箱 verified）或 reject（403 不可登录）。
 */
public interface RegistrationService {

    /**
     * 提交注册申请
     *
     * @return 申请视图（status=PENDING）
     * @throws com.vulnaudit.exception.BusinessException 手机号格式非法 / 验证码未验证或错误 / 用户名·手机号·邮箱重复（409）
     */
    @Nonnull
    RegistrationRequestVO register(@Nonnull RegisterRequest request);

    /**
     * 分页查询注册申请
     *
     * @param status 状态过滤（PENDING/APPROVED/REJECTED，空则全部）
     */
    @Nonnull
    PageResponse<RegistrationRequestVO> list(@Nullable String status, int page, int size);

    /**
     * 审批通过：占位用户转 active，手机/邮箱落库并标记 verified
     */
    @Nonnull
    ApproveRegistrationResponse approve(@Nonnull UUID requestId, @Nullable UUID reviewerId);

    /**
     * 审批拒绝：申请置 REJECTED，占位用户置 rejected（登录 403）
     */
    @Nonnull
    RegistrationRequestVO reject(@Nonnull UUID requestId, @Nullable UUID reviewerId, @Nullable String note);
}
