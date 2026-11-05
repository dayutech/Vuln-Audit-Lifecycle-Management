package com.vulnaudit.service;

import com.vulnaudit.dto.ApproveRegistrationResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.RegisterRequest;
import com.vulnaudit.dto.RegistrationRequestVO;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
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
     *
     * <p>t111（用户拍板）：角色必须由审批人**显式指定**（{@code roles: string[]}）；
     * <b>空数组 = 建号但不授角色</b>（登录后仅默认 ROLE_USER），不再隐式分配 {@code vulnerability_engineer}。
     *
     * @param requestId  申请ID
     * @param reviewerId 审批人
     * @param roleCodes  角色码列表（可空 = 不授角色）
     */
    @Nonnull
    ApproveRegistrationResponse approve(@Nonnull UUID requestId, @Nullable UUID reviewerId,
                                        @Nullable List<String> roleCodes);

    /**
     * 审批拒绝：申请置 REJECTED，占位用户置 rejected（登录 403），并**释放唯一键**（t111）
     */
    @Nonnull
    RegistrationRequestVO reject(@Nonnull UUID requestId, @Nullable UUID reviewerId, @Nullable String note);

    /**
     * 彻底清理申请及其占位用户（t111，管理员）
     *
     * <p>幂等：申请不存在时直接返回（上层回 204）；已通过（APPROVED）的申请拒绝清理（409），
     * 避免把「已激活的账号」删除后同名/同手机可重复注册。
     *
     * @param requestId  申请ID
     * @param operatorId 操作管理员
     */
    void purge(@Nonnull UUID requestId, @Nullable UUID operatorId);
}
