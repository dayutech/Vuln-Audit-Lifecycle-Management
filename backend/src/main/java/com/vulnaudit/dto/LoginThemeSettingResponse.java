package com.vulnaudit.dto;

import java.time.LocalDateTime;

/**
 * 管理员视角的登录页缺省主题设置（t109）
 *
 * <p>{@code GET/PUT /api/v1/admin/settings/login-theme} 的响应体。
 *
 * @param defaultTheme 当前缺省主题代码（缺失/非法时回落 pencil）
 * @param updatedBy    最后修改人用户名（系统自愈插入时为 null）
 * @param updatedAt    最后修改时间（系统自愈插入时为插入时间）
 */
public record LoginThemeSettingResponse(String defaultTheme, String updatedBy, LocalDateTime updatedAt) {
}
