package com.vulnaudit.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClientIpResolver} 单元测试（t188）。
 *
 * <p>对应真实缺陷：5 个 controller 各自无条件下取 {@code X-Forwarded-For} 第一段，
 * 攻击者直连后端端口即可为每次尝试换一个伪造 IP，绕过「5 次/15 分钟」的 IP 级锁定。
 * 这里把「非受信对端不得影响 IP 判定」写成可失败断言。
 */
@DisplayName("客户端 IP 解析（X-Forwarded-For 防伪造）")
class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED_PROXIES);

    @Test
    @DisplayName("非受信对端：伪造 X-Forwarded-For 被忽略，仍返回 socket 对端地址")
    void ignoresForwardedHeaderFromUntrustedPeer() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.188.210");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertEquals("192.168.188.210", resolver.resolve(request));
    }

    @Test
    @DisplayName("非受信对端：X-Real-IP 同样不被采信")
    void ignoresRealIpFromUntrustedPeer() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.188.210");
        request.addHeader("X-Real-IP", "203.0.113.11");

        assertEquals("192.168.188.210", resolver.resolve(request));
    }

    @Test
    @DisplayName("受信代理（回环）：取转发头的最后一段作为真实客户端")
    void trustsForwardedHeaderFromLoopbackProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.12");

        assertEquals("203.0.113.12", resolver.resolve(request));
    }

    @Test
    @DisplayName("受信代理：客户端自带伪造前缀时，以代理追加的最后一段为准")
    void usesLastEntryWhenClientPrependsFakeAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.13");

        assertEquals("203.0.113.13", resolver.resolve(request));
    }

    @Test
    @DisplayName("受信代理：转发头全是垃圾时回退到 X-Real-IP，再回退到对端地址")
    void fallsBackWhenForwardedHeaderIsGarbage() {
        MockHttpServletRequest withRealIp = new MockHttpServletRequest();
        withRealIp.setRemoteAddr("127.0.0.1");
        withRealIp.addHeader("X-Forwarded-For", "not-an-ip, ;;;");
        withRealIp.addHeader("X-Real-IP", "203.0.113.14");
        assertEquals("203.0.113.14", resolver.resolve(withRealIp));

        MockHttpServletRequest withoutRealIp = new MockHttpServletRequest();
        withoutRealIp.setRemoteAddr("127.0.0.1");
        withoutRealIp.addHeader("X-Forwarded-For", "garbage");
        assertEquals("127.0.0.1", resolver.resolve(withoutRealIp));
    }

    @Test
    @DisplayName("IPv6 回环与 ::ffff: 映射写法都视为受信对端")
    void handlesIpv6Forms() {
        MockHttpServletRequest ipv6 = new MockHttpServletRequest();
        ipv6.setRemoteAddr("0:0:0:0:0:0:0:1");
        ipv6.addHeader("X-Forwarded-For", "203.0.113.15");
        assertEquals("203.0.113.15", resolver.resolve(ipv6));

        MockHttpServletRequest mapped = new MockHttpServletRequest();
        mapped.setRemoteAddr("::ffff:127.0.0.1");
        mapped.addHeader("X-Forwarded-For", "203.0.113.16");
        assertEquals("203.0.113.16", resolver.resolve(mapped));
    }

    @Test
    @DisplayName("可按网段配置受信代理（10.0.0.0/8）")
    void supportsCidrTrustedProxies() {
        ClientIpResolver cidrResolver = new ClientIpResolver("127.0.0.1,10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.7.8.9");
        request.addHeader("X-Forwarded-For", "203.0.113.17");

        assertEquals("203.0.113.17", cidrResolver.resolve(request));
        assertTrue(cidrResolver.isTrustedProxy("10.7.8.9"));
        assertTrue(cidrResolver.isTrustedProxy("10.255.255.255"));
        assertFalse(cidrResolver.isTrustedProxy("11.0.0.1"));
        assertFalse(cidrResolver.isTrustedProxy("192.168.188.210"));
    }

    @Test
    @DisplayName("地址合法性判定：拒绝主机名与越界八位组")
    void validatesIpLiterals() {
        assertTrue(ClientIpResolver.isValidIp("192.168.1.1"));
        assertFalse(ClientIpResolver.isValidIp("999.1.1.1"));
        assertFalse(ClientIpResolver.isValidIp("evil.example.com"));
        assertFalse(ClientIpResolver.isValidIp("1.2.3"));
        assertTrue(ClientIpResolver.isValidIp("2001:db8::1"));
    }
}