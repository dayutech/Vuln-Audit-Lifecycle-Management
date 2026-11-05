package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.LoginThemeSettingResponse;
import com.vulnaudit.dto.UpdateLoginThemeSettingRequest;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 系统设置（管理员，t109）
 *
 * <p>当前唯一设置项：登录页缺省主题 {@code login.defaultTheme}。
 * <ul>
 *   <li>{@code GET /api/v1/admin/settings/login-theme}：ADMIN，返回当前缺省值 + 最后修改人/时间；</li>
 *   <li>{@code PUT /api/v1/admin/settings/login-theme}：ADMIN，白名单校验（非法 400 且列出支持列表），
 *       写审计 {@code UPDATE_SYSTEM_SETTING}（details 记录 from→to）。</li>
 * </ul>
 *
 * <p>与用户偏好的边界：本端点只改**全站缺省值**，不会写任何用户的 {@code users.login_theme}；
 * 用户自行选择主题仍走 {@code PUT /api/v1/users/me/preferences}。
 *
 * <p>模拟视角（t90）下这些写端点由 {@code ImpersonationGuardFilter} 拦截为 403（{@code /admin/**} 写方法），
 * 避免「以他人身份改全站配置」。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
@Tag(name = "系统设置（管理员）", description = "全站设置：当前为登录页缺省主题")
@SecurityRequirement(name = "bearerAuth")
public class AdminSettingsController {

    private final SystemSettingService systemSettingService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/login-theme")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "读取登录页缺省主题",
            description = "仅 ADMIN；返回 defaultTheme / updatedBy / updatedAt。缺失或非法值回落 pencil")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非管理员")
    })
    public ApiResponse<LoginThemeSettingResponse> getLoginThemeSetting() {
        return ApiResponse.success(systemSettingService.getLoginThemeSetting());
    }

    @PutMapping("/login-theme")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "修改登录页缺省主题",
            description = "仅 ADMIN；白名单 8 项，非法 400（消息含支持列表）；写审计 UPDATE_SYSTEM_SETTING（from→to）")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "不支持的登录页主题"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非管理员")
    })
    public ApiResponse<LoginThemeSettingResponse> updateLoginThemeSetting(
            @Valid @RequestBody UpdateLoginThemeSettingRequest request) {

        var actorId = currentUserProvider.currentUserId().orElse(null);
        return ApiResponse.success(systemSettingService.updateDefaultLoginTheme(actorId, request.defaultTheme()));
    }
}
