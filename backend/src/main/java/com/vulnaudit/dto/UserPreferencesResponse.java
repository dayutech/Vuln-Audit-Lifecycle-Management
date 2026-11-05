package com.vulnaudit.dto;

/**
 * 用户偏好（t96 / t100 / t112）
 *
 * <p>字段名固定为 {@code loginTheme}（前端 t97/t110 已按此实现）。
 *
 * <p><b>三态语义（t112）</b>：
 * <ul>
 *   <li>{@code loginTheme=null} 且 {@code followDefault=true}：<b>跟随系统默认</b>
 *       （管理员在 {@code GET /api/v1/auth/login-theme} 里给的 {@code defaultTheme}）；</li>
 *   <li>{@code loginTheme=<code>} 且 {@code followDefault=false}：用户指定了具体主题，
 *       管理员之后改缺省不影响它。</li>
 * </ul>
 *
 * <p>之所以显式给 {@code followDefault}：前端老版本只读 {@code loginTheme}，会把 {@code null}
 * 当成「没数据」；带上布尔位后可以明确区分「跟随默认」与「确实没有值」。
 *
 * @param loginTheme    登录页主题代码；{@code null} 表示跟随系统默认
 * @param followDefault 是否跟随系统默认（等价于 {@code loginTheme == null}）
 */
public record UserPreferencesResponse(String loginTheme, boolean followDefault) {
}
