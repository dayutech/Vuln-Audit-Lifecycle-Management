package com.vulnaudit.service;

import com.vulnaudit.dto.UnlockSendCodeResponse;
import jakarta.annotation.Nullable;

/**
 * 账户锁定后的 OTP 解锁（t123）
 *
 * <p>解锁必须走绑定并已验证的手机/邮箱验证码（purpose={@code UNLOCK}）；未绑定任何已验证联系方式时，
 * 由管理员在 {@code POST /admin/users/{id}/unlock} 解锁。
 */
public interface AccountUnlockService {

    /**
     * 发送解锁验证码
     *
     * @param username 用户名
     * @param clientIp 客户端 IP（限流）
     * @return 统一结构响应；用户名不存在或无已验证联系方式时也为成功结构，但不发码（防枚举）
     */
    UnlockSendCodeResponse sendCode(@Nullable String username, @Nullable String clientIp);

    /**
     * 校验验证码并解锁
     *
     * @throws com.vulnaudit.exception.BusinessException 400（用户不存在/target 不匹配/验证码错误）
     */
    void confirm(@Nullable String username, @Nullable String target, @Nullable String code);

    /** 仅供测试：清空解锁相关的发码限流状态 */
    void resetThrottleState();
}
