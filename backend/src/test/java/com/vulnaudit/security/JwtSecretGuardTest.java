package com.vulnaudit.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtSecretGuard} 单元测试（t188）。
 *
 * <p>根因证据：仓库默认值 {@code your-256-bit-secret-key-here-please-change-in-production}
 * 曾随环境变量缺失被静默启用；用它对 {@code sub=admin, roles=ROLE_ADMIN} 自签的 token
 * 在线上 8089/8088 均返回 200。这里把「默认值/弱值必须启动失败」写成可失败断言。
 */
@DisplayName("JWT 密钥启动校验")
class JwtSecretGuardTest {

    @Test
    @DisplayName("仓库默认占位符必须被拒绝（此前正是它被静默启用）")
    void rejectsRepositoryDefault() {
        String problem = JwtSecretGuard.findProblem("your-256-bit-secret-key-here-please-change-in-production");
        assertNotNull(problem);
        assertTrue(problem.contains("默认占位值"), problem);
        assertThrows(IllegalStateException.class,
                () -> JwtSecretGuard.validate("your-256-bit-secret-key-here-please-change-in-production"));
    }

    @Test
    @DisplayName("your- 前缀的其他占位值同样拒绝")
    void rejectsYourPrefix() {
        assertNotNull(JwtSecretGuard.findProblem("your-own-secret-please-rotate-000000000000"));
    }

    @Test
    @DisplayName("空值与 null 拒绝")
    void rejectsBlank() {
        assertNotNull(JwtSecretGuard.findProblem(null));
        assertNotNull(JwtSecretGuard.findProblem("   "));
    }

    @Test
    @DisplayName("短于 32 字符的弱密钥拒绝")
    void rejectsShortSecrets() {
        assertNotNull(JwtSecretGuard.findProblem("short-secret"));
    }

    @Test
    @DisplayName("字符种类过少的重复填充拒绝")
    void rejectsLowEntropy() {
        assertNotNull(JwtSecretGuard.findProblem("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    }

    @Test
    @DisplayName("随机长密钥通过（测试环境实际使用的值也应通过）")
    void acceptsStrongSecrets() {
        assertNull(JwtSecretGuard.findProblem("dGhpcy1pcy1hLXN0cm9uZy1yYW5kb20tc2VjcmV0LWZvci10ZXN0cy0xMjM0NTY3ODkw"));
        assertNull(JwtSecretGuard.findProblem("test-secret-key-for-unit-testing-must-be-at-least-256-bits-long"));
    }
}