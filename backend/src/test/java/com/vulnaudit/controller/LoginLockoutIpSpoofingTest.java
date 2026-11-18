package com.vulnaudit.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录锁定防绕过测试（t188）。
 *
 * <p>缺陷现场：{@code resolveClientIp} 无条件采信 {@code X-Forwarded-For} 第一段，
 * 且后端监听 0.0.0.0:8088（nginx 只是其中一条入口）。攻击者直连 8088，
 * 每次失败换一个伪造 IP，IP 级锁定永远不触发，等于无限次在线爆破。
 *
 * <p>修复口径：只有直连对端是受信代理时才读转发头。本用例把「对端为公网地址 +
 * 伪造转发头」摆出来：连续 5 次失败后，即使再换一个新伪造 IP，也必须仍是 429。
 */
@DisplayName("登录锁定：伪造 X-Forwarded-For 不能绕过 IP 级锁定")
class LoginLockoutIpSpoofingTest extends BaseTest {

    private static final String ATTACKER_PEER = "203.0.113.9";
    private static final String BODY = "{\"username\":\"zz_no_such_user_9f3a\",\"password\":\"definitely-wrong\"}";

    private int attempt(String forwardedFor) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(ATTACKER_PEER);
                            return request;
                        })
                        .header("X-Forwarded-For", forwardedFor)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("轮换伪造 IP 后仍处于 IP 锁定期（429），而不是被重置为 401")
    void rotatingSpoofedXffCannotResetIpLock() throws Exception {
        for (int i = 0; i < 5; i++) {
            int status = attempt("10.9.9." + i);
            org.junit.jupiter.api.Assertions.assertEquals(401, status,
                    "第 " + (i + 1) + " 次错误密码应为 401");
        }

        int sixth = attempt("10.9.9.99");
        org.junit.jupiter.api.Assertions.assertEquals(429, sixth,
                "换一个伪造 IP 也必须继续 429（修复前此处是 401，说明锁定被绕过）");
    }

    @Test
    @DisplayName("锁定响应带 Retry-After，客户端可感知")
    void lockedResponseCarriesRetryAfter() throws Exception {
        for (int i = 0; i < 5; i++) {
            attempt("10.8.8." + i);
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(ATTACKER_PEER);
                            return request;
                        })
                        .header("X-Forwarded-For", "10.8.8.100")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }
}