package com.vulnaudit.controller;

import com.vulnaudit.dto.ChangePasswordRequest;
import com.vulnaudit.dto.LoginRequest;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 认证模块测试类
 * 测试登录、登出、刷新Token、获取当前用户信息等功能
 */
@DisplayName("认证模块测试")
public class AuthControllerTest extends BaseTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Nested
    @DisplayName("用户登录测试")
    class LoginTests {

        @Test
        @DisplayName("正常登录 - 成功")
        void login_Success() throws Exception {
            // 准备测试数据
            User user = User.builder()
                    .username("testuser")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .email("test@example.com")
                    .fullName("测试用户")
                    .status("active")
                    .build();
            userRepository.save(Objects.requireNonNull(user));

            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password123");

            // 执行登录请求
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.token").exists())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.username").value("testuser"));
        }

        @Test
        @DisplayName("登录失败 - 用户不存在")
        void login_Fail_UserNotFound() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("nonexistent");
            request.setPassword("password123");

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("登录失败 - 密码错误")
        void login_Fail_WrongPassword() throws Exception {
            // 准备测试数据
            User user = User.builder()
                    .username("testuser2")
                    .passwordHash(passwordEncoder.encode("correctpassword"))
                    .email("test2@example.com")
                    .fullName("测试用户2")
                    .status("active")
                    .build();
            userRepository.save(Objects.requireNonNull(user));

            LoginRequest request = new LoginRequest();
            request.setUsername("testuser2");
            request.setPassword("wrongpassword");

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("登录失败 - 连续失败触发限流 429")
        void login_Fail_TooManyAttempts() throws Exception {
            User user = User.builder()
                    .username("lockuser")
                    .passwordHash(passwordEncoder.encode("correctpassword"))
                    .email("lock@example.com")
                    .fullName("限流测试用户")
                    .status("active")
                    .build();
            userRepository.save(Objects.requireNonNull(user));

            LoginRequest request = new LoginRequest();
            request.setUsername("lockuser");
            request.setPassword("wrongpassword");
            String body = Objects.requireNonNull(objectMapper.writeValueAsString(request));

            // 默认阈值 5 次：前 5 次 401，第 6 次返回 429 并带 Retry-After
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                .content(body))
                        .andExpect(status().isUnauthorized());
            }
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                            .content(body))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"));
        }

        @Test
        @DisplayName("登录失败 - 用户已禁用")
        void login_Fail_UserDisabled() throws Exception {
            // 准备测试数据 - 用户状态为inactive
            User user = User.builder()
                    .username("disableduser")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .email("disabled@example.com")
                    .fullName("已禁用用户")
                    .status("inactive")
                    .build();
            userRepository.save(Objects.requireNonNull(user));

            LoginRequest request = new LoginRequest();
            request.setUsername("disableduser");
            request.setPassword("password123");

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("获取当前用户测试")
    class GetCurrentUserTests {

        @Test
        @DisplayName("获取当前用户信息 - 成功")
        void getCurrentUser_Success() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("修改密码测试")
    class ChangePasswordTests {

        @Test
        @DisplayName("修改密码 - 成功")
        void changePassword_Success() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("engineer123");
            request.setNewPassword("newpassword123");

            mockMvc.perform(post("/api/v1/auth/change-password")
                    .header("Authorization", engineerToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("修改密码 - 旧密码错误")
        void changePassword_Fail_WrongOldPassword() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("wrongoldpassword");
            request.setNewPassword("newpassword123");

            mockMvc.perform(post("/api/v1/auth/change-password")
                    .header("Authorization", engineerToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("用户登出测试")
    class LogoutTests {

        @Test
        @DisplayName("用户登出 - 成功")
        void logout_Success() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }
}
