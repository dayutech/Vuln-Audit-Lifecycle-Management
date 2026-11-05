package com.vulnaudit.dto;

import com.vulnaudit.entity.enums.LoginTheme;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 更新用户偏好请求（t96 / t100 / t112）
 *
 * <p><b>只能改自己</b>：控制器固定使用当前登录用户（{@code /users/me/preferences}），
 * 请求体里即便带 {@code userId} 也会被忽略（不存在该字段）。
 *
 * <p><b>三态语义（t112）</b>：{@code loginTheme} 与 {@code followDefault} 二选一——
 * <ul>
 *   <li>{@code {"loginTheme":"owl"}} → 用户指定主题（写入 {@code users.login_theme='owl'}）；</li>
 *   <li>{@code {"followDefault":true}} → 清回<b>跟随系统默认</b>（{@code users.login_theme=NULL}，
 *       以后管理员改缺省，该用户自动跟随）；</li>
 *   <li>两者同传、或都不传 → <b>400</b>（语义歧义，不猜）。</li>
 * </ul>
 *
 * <p>白名单单一来源仍是 {@link LoginTheme}；本 DTO 只做结构校验（长度 + 二选一由服务层判定，
 * 以便 400 文案走 t60 的 MessageSource 做中英切换）。
 *
 * @param loginTheme    登录页主题代码（{@link LoginTheme#getCode()}，大小写与首尾空格不敏感），可选
 * @param followDefault 是否跟随系统默认（true 时清空个人主题），可选
 */
public record UpdateUserPreferencesRequest(
        @Size(max = LoginTheme.MAX_CODE_LENGTH, message = "validation.user.login_theme.too_long")
        @Schema(description = "登录页主题（8 项：pencil / geometric / owl / pixel-eyes / aurora / mono-grid / orbit / big-type）；"
                + "与 followDefault 二选一", example = "owl")
        String loginTheme,

        @Schema(description = "跟随系统默认（true 时把个人主题清为 NULL，改由管理员缺省决定）；与 loginTheme 二选一",
                example = "true")
        Boolean followDefault) {
}
