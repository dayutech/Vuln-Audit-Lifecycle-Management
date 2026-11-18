package com.vulnaudit.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulnaudit.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户实体序列化脱敏测试（t188）。
 *
 * <p>缺陷现场：{@code User} 的 {@code totpSecret}（TOTP 密钥密文）没有 {@code @JsonIgnore}，
 * 而 {@code GET /users/{id}}、{@code GET /users}（管理员/组长）、{@code PUT /users/{id}}
 * 都直接返回实体。任何能读用户列表的角色都能批量拿到全员的 TOTP 密文
 * （配合主密钥泄漏即可直接伪造 2FA 码）。密码散列此前已用 {@code @JsonIgnore} 挡住，TOTP 漏了。
 */
@DisplayName("用户实体序列化：不得带出密钥类字段")
class UserSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("totpSecret / passwordHash 不出现在 JSON 中")
    void hidesSecretsFromJson() throws Exception {
        User user = Objects.requireNonNull(User.builder()
                .username("u_sec")
                .passwordHash("$2a$10$hash-should-not-leak")
                .email("u_sec@test.com")
                .fullName("安全测试")
                .status("active")
                .totpSecret("v1:ciphertext-should-not-leak")
                .build());

        String json = objectMapper.writeValueAsString(user);

        assertFalse(json.contains("totpSecret"), "不应序列化 totpSecret: " + json);
        assertFalse(json.contains("ciphertext-should-not-leak"), "不应泄漏 TOTP 密文: " + json);
        assertFalse(json.contains("passwordHash"), "不应序列化 passwordHash: " + json);
        assertFalse(json.contains("hash-should-not-leak"), "不应泄漏口令散列: " + json);
        // 正常的对外字段仍应保留
        assertTrue(json.contains("\"username\":\"u_sec\""), "username 应正常序列化: " + json);
    }
}