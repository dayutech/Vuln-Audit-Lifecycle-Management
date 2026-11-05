package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 审批通过请求（t111）
 *
 * <p><b>角色必须显式指定</b>：{@code roles} 为空数组（或省略）表示「建号但不授角色」，
 * 该账号登录后只有默认 {@code ROLE_USER}，没有任何业务权限。
 * **不再隐式分配 {@code vulnerability_engineer}** —— 授权由审批人明确选择。
 *
 * @param roles 角色码列表（如 ["vulnerability_engineer", "team_leader"]）
 */
@Schema(description = "审批通过请求")
public record ApproveRegistrationRequest(
        @Schema(description = "要授予的角色码列表；空数组=不授角色", example = "[\"vulnerability_engineer\"]")
        List<String> roles) {
}
