package com.vulnaudit.controller;

import com.vulnaudit.config.JwtProperties;
import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.ChangePasswordRequest;
import com.vulnaudit.dto.LoginRequest;
import com.vulnaudit.dto.LoginResponse;
import com.vulnaudit.entity.User;
import com.vulnaudit.security.JwtUtils;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证控制器
 * 
 * 功能说明：
 * - 处理用户登录请求
 * - 处理用户登出请求
 * - 处理密码修改请求
 * - 处理令牌刷新请求
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录、登出、密码修改等接口")
public class AuthController {

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回JWT令牌")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功", 
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "用户名或密码错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "用户账号已被禁用")
    })
    public ApiResponse<LoginResponse> login(
            @Parameter(description = "登录请求", required = true) 
            @Valid @RequestBody LoginRequest request) {
        log.info("用户登录请求: username={}", request.getUsername());
        
        User user = userService.authenticate(request.getUsername(), request.getPassword());
        userService.updateLastLogin(user.getId());
        
        List<String> roles = userService.getUserRoleCodes(user.getId());
        List<String> permissions = userService.getUserPermissionCodes(user.getId());
        
        String token = jwtUtils.generateToken(user.getUsername(), 
                roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toList()));
        
        LoginResponse response = LoginResponse.builder()
                .token(token)
                .tokenType(jwtProperties.getPrefix().trim())
                .expiresIn(jwtProperties.getExpiration())
                .userId(user.getId().toString())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
        
        log.info("用户登录成功: username={}", user.getUsername());
        return ApiResponse.success(response);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户登出，清除服务端会话（JWT无状态，客户端清除令牌即可）")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登出成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权")
    })
    public ApiResponse<Void> logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            log.info("用户登出: username={}", authentication.getName());
        }
        return ApiResponse.success(null);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权")
    })
    public ApiResponse<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            log.warn("未找到认证信息");
            return ApiResponse.error("未授权");
        }
        
        String username = authentication.getName();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        user.setPasswordHash(null);
        return ApiResponse.success(user);
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    @Operation(summary = "修改密码", description = "用户修改自己的密码")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "密码修改成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "旧密码错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权")
    })
    public ApiResponse<Void> changePassword(
            @Parameter(description = "修改密码请求", required = true)
            @Valid @RequestBody ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            log.warn("未找到认证信息");
            return ApiResponse.error("未授权");
        }
        
        String username = authentication.getName();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        boolean success = userService.changePassword(user.getId(), request.getOldPassword(), request.getNewPassword());
        
        if (success) {
            log.info("密码修改成功: username={}", username);
            return ApiResponse.success(null);
        } else {
            log.warn("密码修改失败: username={}", username);
            return ApiResponse.error("密码修改失败");
        }
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用当前有效令牌刷新获取新令牌")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "刷新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权")
    })
    public ApiResponse<LoginResponse> refreshToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            log.warn("未找到认证信息");
            return ApiResponse.error("未授权");
        }
        
        String username = authentication.getName();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        List<String> roles = userService.getUserRoleCodes(user.getId());
        
        String token = jwtUtils.generateToken(username, 
                roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toList()));
        
        LoginResponse response = LoginResponse.builder()
                .token(token)
                .tokenType(jwtProperties.getPrefix().trim())
                .expiresIn(jwtProperties.getExpiration())
                .userId(user.getId().toString())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
        
        log.info("令牌刷新成功: username={}", username);
        return ApiResponse.success(response);
    }

}
