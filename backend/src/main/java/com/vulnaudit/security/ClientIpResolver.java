package com.vulnaudit.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 客户端 IP 解析器（单一事实来源）。
 *
 * <p><b>为什么需要它</b>：登录锁定、验证码限流、找回密码失败限流都以客户端 IP 为键。
 * 此前 5 个 controller 各自复制了一份实现：
 * <pre>String forwarded = request.getHeader("X-Forwarded-For");
 * if (forwarded != null &amp;&amp; !forwarded.isBlank()) return forwarded.split(",")[0].trim();
 * return request.getRemoteAddr();</pre>
 * 它<b>无条件采信</b>客户端提交的 {@code X-Forwarded-For}。只要请求能绕过 nginx
 * 直达后端端口，攻击者就能为每次尝试换一个伪造 IP，使「5 次/15 分钟」的 IP 级锁定完全失效
 * （2026-11 实测：直连 8088 时，伪装 IP 连续 5 次失败后换下一个伪装 IP 立刻回到 401）。
 *
 * <p><b>正确口径</b>：
 * <ol>
 *   <li>只有<b>直连对端</b>（{@code request.getRemoteAddr()}）本身是受信反向代理时，才读转发头；</li>
 *   <li>转发头取<b>最后一段</b>——那是离我们最近的代理写入的真实对端地址
 *       （nginx 用 {@code $proxy_add_x_forwarded_for} 追加、用 {@code $remote_addr} 覆盖，
 *       两种写法下「最后一段」都是真实客户端）；</li>
 *   <li>非受信对端提交的转发头一律忽略，直接用自己的 socket 对端地址。</li>
 * </ol>
 *
 * <p>受信代理列表由 {@code app.security.trusted-proxies} 配置（逗号分隔，支持 {@code 1.2.3.4}
 * 精确匹配与 {@code 10.0.0.0/8} 网段），默认只有回环地址——即生产形态「nginx 与后端同机」。
 */
@Slf4j
@Component
public class ClientIpResolver {

    /** 默认只信任本机回环（nginx 与后端同机部署） */
    public static final String DEFAULT_TRUSTED_PROXIES = "127.0.0.1,::1,0:0:0:0:0:0:0:1";

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]+$");
    private static final Pattern IPV4_CIDR = Pattern.compile("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})/(\\d{1,2})$");

    private final List<String> exactProxies = new ArrayList<>();
    private final List<CidrRange> cidrProxies = new ArrayList<>();

    public ClientIpResolver(@Value("${app.security.trusted-proxies:" + DEFAULT_TRUSTED_PROXIES + "}") String trustedProxies) {
        String raw = StringUtils.hasText(trustedProxies) ? trustedProxies : DEFAULT_TRUSTED_PROXIES;
        for (String token : raw.split(",")) {
            String value = normalize(token);
            if (value == null || value.isEmpty()) {
                continue;
            }
            var matcher = IPV4_CIDR.matcher(value);
            if (matcher.matches()) {
                CidrRange range = CidrRange.of(matcher.group(1), Integer.parseInt(matcher.group(2)));
                if (range != null) {
                    cidrProxies.add(range);
                }
                continue;
            }
            if (isValidIp(value)) {
                exactProxies.add(value);
            } else {
                log.warn("忽略非法的受信代理配置项: {}", token.trim());
            }
        }
        log.info("受信反向代理: exact={}, cidr={}", exactProxies, cidrProxies);
    }

    /**
     * 解析真实客户端 IP。
     *
     * @param request 当前请求
     * @return 真实客户端 IP；无法确定时返回 socket 对端地址
     */
    public String resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (!StringUtils.hasText(remote)) {
            return "unknown";
        }
        if (!isTrustedProxy(remote)) {
            // 裸奔直连（或来自非受信代理）：绝不采信转发头，否则锁定/限流可被伪造
            return remote;
        }

        String fromForwardedFor = lastForwardedAddress(request.getHeader("X-Forwarded-For"));
        if (fromForwardedFor != null) {
            return fromForwardedFor;
        }
        String realIp = normalize(request.getHeader("X-Real-IP"));
        if (isValidIp(realIp)) {
            return Objects.requireNonNull(realIp);
        }
        return remote;
    }

    /**
     * 判断给定地址是否为受信的直连代理。
     *
     * @param ip 地址
     * @return true 表示来自受信代理
     */
    public boolean isTrustedProxy(@Nullable String ip) {
        String value = normalize(ip);
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (exactProxies.contains(value)) {
            return true;
        }
        return cidrProxies.stream().anyMatch(range -> range.contains(value));
    }

    /**
     * 取 X-Forwarded-For 里<b>最后一个</b>合法地址。
     *
     * @param header 头部原值
     * @return 合法地址；全都不合法时返回 null
     */
    @Nullable
    private String lastForwardedAddress(@Nullable String header) {
        if (!StringUtils.hasText(header)) {
            return null;
        }
        String[] parts = header.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = normalize(parts[i]);
            if (isValidIp(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 规范化：去空白、去 IPv6 方括号、去 {@code ::ffff:} 映射前缀、转小写。
     *
     * @param ip 原始地址
     * @return 规范化地址；null 表示无值
     */
    @Nullable
    static String normalize(@Nullable String ip) {
        if (ip == null) {
            return null;
        }
        String value = ip.trim();
        if (value.isEmpty()) {
            return value;
        }
        if (value.startsWith("[") && value.endsWith("]") && value.length() > 2) {
            value = value.substring(1, value.length() - 1).trim();
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("::ffff:") && isValidIpv4(lower.substring(7))) {
            return lower.substring(7);
        }
        return lower;
    }

    /**
     * 是否为合法 IP 字面量（不触发 DNS 解析）。
     *
     * @param ip 规范化后的地址
     * @return true 表示合法
     */
    static boolean isValidIp(@Nullable String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        if (isValidIpv4(ip)) {
            return true;
        }
        // IPv6 粗判：必须含冒号，且只由十六进制字符、冒号、点组成
        return ip.indexOf(':') >= 0 && IPV6.matcher(ip).matches() && ip.split(":").length >= 3;
    }

    private static boolean isValidIpv4(String ip) {
        var matcher = IPV4.matcher(ip);
        if (!matcher.matches()) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            int octet;
            try {
                octet = Integer.parseInt(matcher.group(i));
            } catch (NumberFormatException e) {
                return false;
            }
            if (octet > 255) {
                return false;
            }
        }
        return true;
    }

    /** IPv4 网段（如 10.0.0.0/8） */
    private record CidrRange(long network, int prefixLength) {

        static CidrRange of(String base, int prefixLength) {
            if (prefixLength < 0 || prefixLength > 32 || !isValidIpv4(base)) {
                return null;
            }
            String[] parts = base.split("\\.");
            long value = 0;
            for (String part : parts) {
                value = (value << 8) | Integer.parseInt(part);
            }
            long mask = prefixLength == 0 ? 0 : (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            return new CidrRange(value & mask, prefixLength);
        }

        boolean contains(String ip) {
            if (!isValidIpv4(ip)) {
                return false;
            }
            String[] parts = ip.split("\\.");
            long value = 0;
            for (String part : parts) {
                value = (value << 8) | Integer.parseInt(part);
            }
            long mask = prefixLength == 0 ? 0 : (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            return (value & mask) == network;
        }
    }
}