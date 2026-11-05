package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 审批通过结果（t102）
 *
 * @param request     注册申请（状态已变为 APPROVED）
 * @param userId      新创建（转正）的本地用户ID
 * @param username    用户名
 * @param loginEnabled 该用户当前是否可登录（应恒为 true）
 */
@Schema(description = "审批通过结果")
public record ApproveRegistrationResponse(
        RegistrationRequestVO request,
        UUID userId,
        String username,
        boolean loginEnabled) {
}
