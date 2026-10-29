package com.vulnaudit.controller;

import com.vulnaudit.entity.User;
import com.vulnaudit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户密码字段完整性测试（t22）。
 *
 * <p>回归的缺陷：用户接口为了「不返回密码」把受管实体的 {@code passwordHash} 置 null，
 * 在单事务/长会话下把 NULL 刷进 {@code users.password_hash}（NOT NULL）导致 500，
 * 同时说明实体被直接序列化、存在哈希外泄风险。
 *
 * <p>覆盖：
 * <ol>
 *   <li>同一事务内连续 PATCH 状态 → PUT 用户 → DELETE 用户，不抛约束异常，库里哈希始终非空；</li>
 *   <li>GET 列表/详情、PATCH 状态、PUT 用户、GET /auth/me 的响应 JSON 都不含 passwordHash；</li>
 *   <li>改密码经 BCrypt 加密：新密码可登录、旧密码 401；管理员重置密码同样加密；</li>
 *   <li>软删除保留哈希原值。</li>
 * </ol>
 */
@DisplayName("用户密码字段完整性测试")
public class UserPasswordIntegrityTest extends BaseTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User targetUser;
    private String targetUsername;

    @BeforeEach
    void setUpTargetUser() {
        String suffix = String.valueOf(System.currentTimeMillis());
        targetUser = userRepository.save(Objects.requireNonNull(User.builder()
                .username("pwuser_" + suffix)
                .passwordHash(passwordEncoder.encode("origin123"))
                .email("pwuser_" + suffix + "@test.com")
                .fullName("密码完整性用例")
                .status("active")
                .build()));
        targetUsername = targetUser.getUsername();
    }

    /** 读取数据库中的最新状态（绕过一级缓存） */
    private User reloadTarget() {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findById(Objects.requireNonNull(targetUser.getId())).orElseThrow();
    }

    private String adminUsername() {
        return userRepository.findById(Objects.requireNonNull(adminUserId))
                .orElseThrow()
                .getUsername();
    }

    // ------------------------------------------------------------------ 约束隐患

    @Test
    @DisplayName("同一事务内 PATCH 状态 → PUT 用户 → DELETE 用户：不抛约束异常且哈希不丢")
    void sequentialWritesOnManagedEntity_doNotNullPasswordHash() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + targetUser.getId() + "/status")
                        .header("Authorization", adminToken)
                        .param("status", "suspended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"fullName\":\"改名后的用户\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("改名后的用户"));

        mockMvc.perform(delete("/api/v1/users/" + targetUser.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        // 关键：曾在此处触发 users.password_hash NOT NULL 约束（拦截器审计插入触发 flush）
        entityManager.flush();

        User reloaded = reloadTarget();
        assertEquals("deactivated", reloaded.getStatus(), "软删除应把状态置为 deactivated");
        assertNotNull(reloaded.getPasswordHash(), "软删除后密码哈希不应为 null");
        assertTrue(passwordEncoder.matches("origin123", reloaded.getPasswordHash()),
                "软删除不应改变原密码");
    }

    @Test
    @DisplayName("改密码后哈希仍非空且可继续更新用户信息")
    void updateAfterPasswordChange_keepsHash() throws Exception {
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"oldPassword\":\"admin123\",\"newPassword\":\"brandNew789\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/users/" + adminUserId)
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"fullName\":\"管理员改名\"}"))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        User admin = userRepository.findById(Objects.requireNonNull(adminUserId)).orElseThrow();
        assertNotNull(admin.getPasswordHash());
        assertTrue(passwordEncoder.matches("brandNew789", admin.getPasswordHash()),
                "更新用户信息不应影响已修改的密码");
    }

    // ------------------------------------------------------------------ 哈希外泄

    @Test
    @DisplayName("各用户相关响应的 JSON 均不含 passwordHash 字段")
    void responses_neverExposePasswordHash() throws Exception {
        String listBody = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", adminToken)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(listBody.contains("passwordHash"), "GET /users 响应不应包含 passwordHash");
        assertFalse(listBody.contains(targetUser.getPasswordHash()), "响应不应包含哈希值本身");

        String detailBody = mockMvc.perform(get("/api/v1/users/" + targetUser.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(detailBody.contains("passwordHash"), "GET /users/{id} 响应不应包含 passwordHash");

        String statusBody = mockMvc.perform(patch("/api/v1/users/" + targetUser.getId() + "/status")
                        .header("Authorization", adminToken)
                        .param("status", "active"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(statusBody.contains("passwordHash"), "PATCH /users/{id}/status 响应不应包含 passwordHash");

        String updateBody = mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"position\":\"安全工程师\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(updateBody.contains("passwordHash"), "PUT /users/{id} 响应不应包含 passwordHash");

        String meBody = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(meBody.contains("passwordHash"), "GET /auth/me 响应不应包含 passwordHash");
    }

    // ------------------------------------------------------------------ 改密码生效

    @Test
    @DisplayName("自助改密码：新密码可登录、旧密码失败，库里是 BCrypt 哈希")
    void changePassword_takesEffect() throws Exception {
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"oldPassword\":\"admin123\",\"newPassword\":\"newAdmin456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        entityManager.flush();
        entityManager.clear();
        String hash = userRepository.findById(Objects.requireNonNull(adminUserId)).orElseThrow().getPasswordHash();
        assertTrue(hash.startsWith("$2"), "密码应为 BCrypt 哈希，实际: " + hash);
        assertFalse(hash.contains("newAdmin456"), "库中不应出现明文密码");

        // 新密码可登录
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"username\":\"" + adminUsername() + "\",\"password\":\"newAdmin456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        // 旧密码失败
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"username\":\"" + adminUsername() + "\",\"password\":\"admin123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("管理员重置密码：加密写入，重置后新密码可登录")
    void adminResetPassword_encodes() throws Exception {
        mockMvc.perform(post("/api/v1/users/" + targetUser.getId() + "/reset-password")
                        .header("Authorization", adminToken)
                        .param("newPassword", "resetPass789"))
                .andExpect(status().isOk());

        User reloaded = reloadTarget();
        assertTrue(reloaded.getPasswordHash().startsWith("$2"), "重置后的密码应为 BCrypt 哈希");
        assertTrue(passwordEncoder.matches("resetPass789", reloaded.getPasswordHash()));
        assertFalse(passwordEncoder.matches("origin123", reloaded.getPasswordHash()), "旧密码应失效");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"username\":\"" + targetUsername + "\",\"password\":\"resetPass789\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }
}
