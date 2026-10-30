package com.vulnaudit.dto;

import java.util.UUID;

/**
 * 用户目录条目（最小暴露）
 *
 * <p>供全员使用的下拉/展示数据：<b>只含 id / username / fullName</b>。
 * 这里用专用 record 而不是返回 {@code User} 实体，是为了从类型层面保证
 * email、phone、status、roles、lastLoginAt、passwordHash 等字段<b>不可能</b>被序列化出去
 * （实体上新增字段也不会意外泄漏）。
 *
 * @param id       用户ID
 * @param username 登录名
 * @param fullName 显示名（可能为空）
 */
public record UserDirectoryEntry(UUID id, String username, String fullName) {
}
