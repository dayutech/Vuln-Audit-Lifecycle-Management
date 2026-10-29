package com.vulnaudit.dto;

import java.util.UUID;

/**
 * 批量导入成功创建的用户
 *
 * @param line               行号（1 基，表头为第 1 行）
 * @param userId             用户ID
 * @param username           用户名
 * @param roleCode           绑定角色代码
 * @param initialPassword    系统生成的初始密码（仅本次响应返回，请管理员线下分发）
 * @param mustChangePassword 是否必须修改初始密码（当前 users 表无 must_change_password 列，
 *                           故以本标记 + 管理员重置密码流程约束）
 */
public record ImportedUser(
        int line,
        UUID userId,
        String username,
        String roleCode,
        String initialPassword,
        boolean mustChangePassword
) {
}
