package com.vulnaudit.controller;

import com.vulnaudit.BaseTest;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
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
            userRepository.save(user);

            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password123");

            // 执行登录请求
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").exists())
                    .andExpect(jsonPath("$.data.refreshToken").exists())
                    .andExpect(jsonPath("$.data.tokenType").value("bearer"))
                    .andExpect(jsonPath("$.data.user.username").value("testuser"));
        }

        @Test
        @DisplayName("登录失败 - 用户不存在")
        void login_Fail_UserNotFound() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("nonexistent");
            request.setPassword("password123");

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
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
            userRepository.save(user);

            LoginRequest request = new LoginRequest();
            request.setUsername("testuser2");
            request.setPassword("wrongpassword");

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("登录失败 - 用户被禁用")
        void login_Fail_UserDisabled() throws Exception {
            User user = User.builder()
                    .username("disableduser")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .email("disabled@example.com")
                    .fullName("禁用用户")
                    .status("disabled")
                    .build();
            userRepository.save(user);

            LoginRequest request = new LoginRequest();
            request.setUsername("disableduser");
            request.setPassword("password123");

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("获取当前用户信息测试")
    class GetCurrentUserTests {

        @Test
        @DisplayName("获取当前用户信息 - 成功")
        void getCurrentUser_Success() throws Exception {
            // 准备测试数据
            User user = User.builder()
                    .username("currentuser")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .email("current@example.com")
                    .fullName("当前用户")
                    .status("active")
                    .build();
            userRepository.save(user);

            String token = generateTestToken("currentuser", "ROLE_USER");

            mockMvc.perform(get("/api/v1/auth/me")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("currentuser"))
                    .andExpect(jsonPath("$.email").value("current@example.com"));
        }

        @Test
        @DisplayName("获取当前用户信息 - 未登录")
        void getCurrentUser_Unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("修改密码测试")
    class ChangePasswordTests {

        @Test
        @DisplayName("修改密码 - 成功")
        void changePassword_Success() throws Exception {
            User user = User.builder()
                    .username("pwduser")
                    .passwordHash(passwordEncoder.encode("oldpassword"))
                    .email("pwd@example.com")
                    .fullName("密码用户")
                    .status("active")
                    .build();
            userRepository.save(user);

            String token = generateTestToken("pwduser", "ROLE_USER");

            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("oldpassword");
            request.setNewPassword("newpassword123");

            mockMvc.perform(post("/api/v1/auth/change-password")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("修改密码 - 原密码错误")
        void changePassword_Fail_WrongOldPassword() throws Exception {
            User user = User.builder()
                    .username("pwduser2")
                    .passwordHash(passwordEncoder.encode("correctpassword"))
                    .email("pwd2@example.com")
                    .fullName("密码用户2")
                    .status("active")
                    .build();
            userRepository.save(user);

            String token = generateTestToken("pwduser2", "ROLE_USER");

            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("wrongpassword");
            request.setNewPassword("newpassword123");

            mockMvc.perform(post("/api/v1/auth/change-password")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("用户登出测试")
    class LogoutTests {

        @Test
        @DisplayName("用户登出 - 成功")
        void logout_Success() throws Exception {
            String token = generateTestToken("logoutuser", "ROLE_USER");

            mockMvc.perform(post("/api/v1/auth/logout")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
