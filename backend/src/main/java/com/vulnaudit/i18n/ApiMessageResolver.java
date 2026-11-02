package com.vulnaudit.i18n;

import com.vulnaudit.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.Objects;

/**
 * API 消息解析器（t60）
 *
 * <p>职责：把「消息码」解析成当前请求语言的文案。
 *
 * <p>语言选择顺序：
 * <ol>
 *   <li>查询参数 {@code ?lang=en} / {@code ?lang=zh-CN}（显式覆盖，便于 curl 与联调）；</li>
 *   <li>请求头 {@code Accept-Language}；</li>
 *   <li>都没有 → <b>中文</b>（与当前线上行为一致，不破坏既有前端与冒烟断言）。</li>
 * </ol>
 *
 * <p>回退链：语言包命中 → 默认（中文）包命中 → {@code fallback} 参数（通常是 {@link ErrorCode#getMessage()}）→ 消息码本身。
 * 任何一步缺失都不会抛异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiMessageResolver {

    /** 支持的语言：zh（默认） / en */
    private static final Locale ZH = Locale.SIMPLIFIED_CHINESE;
    private static final Locale EN = Locale.ENGLISH;

    /** 消息码前缀白名单：只有这些前缀才当作「消息码」去解析，避免把自定义中文文案当 key。 */
    private static final String[] KEY_PREFIXES = {"error.", "validation."};

    private final MessageSource messageSource;

    // ------------------------------------------------------------------ 语言

    /**
     * 当前请求应使用的语言（默认中文）
     */
    public Locale currentLocale() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            String langParam = request.getParameter("lang");
            if (StringUtils.hasText(langParam)) {
                return toSupportedLocale(langParam);
            }
            String acceptLanguage = request.getHeader("Accept-Language");
            if (StringUtils.hasText(acceptLanguage)) {
                return toSupportedLocale(acceptLanguage);
            }
        }
        return ZH;
    }

    /**
     * 把任意语言标记归一为受支持的语言（英语 → EN，其余 → 中文）
     */
    public Locale toSupportedLocale(String tagOrHeader) {
        if (!StringUtils.hasText(tagOrHeader)) {
            return ZH;
        }
        String normalized = tagOrHeader.trim().toLowerCase(Locale.ROOT);
        // Accept-Language 可能是 "en-US,en;q=0.9" 这类列表；以第一个语言片段为准
        String first = normalized.split(",")[0].trim();
        return first.startsWith("en") ? EN : ZH;
    }

    private HttpServletRequest currentRequest() {
        try {
            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                return servletAttributes.getRequest();
            }
        } catch (RuntimeException e) {
            // 非 Web 上下文（单元测试直调）时安全降级
            log.debug("无请求上下文，使用默认语言: {}", e.getMessage());
        }
        return null;
    }

    // ------------------------------------------------------------------ 解析

    /**
     * 是否像「消息码」（用于区分自定义中文文案与消息码）
     */
    public boolean isMessageKey(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String candidate = text.trim();
        for (String prefix : KEY_PREFIXES) {
            if (candidate.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析消息码；缺失时返回 {@code fallback}（可为 null）
     */
    public String resolve(String key, String fallback, Object... args) {
        if (!StringUtils.hasText(key)) {
            return fallback;
        }
        Locale locale = currentLocale();
        try {
            return messageSource.getMessage(key, args, locale);
        } catch (NoSuchMessageException e) {
            log.debug("消息码缺失，回退: key={}, locale={}", key, locale);
            return fallback;
        }
    }

    /**
     * 解析「可能是消息码、也可能是已经写好的文案」的字符串：
     * 只有命中 {@code error.} / {@code validation.} 前缀才去查语言包，其余原样返回。
     */
    public String resolveOrKeep(String text) {
        if (!isMessageKey(text)) {
            return text;
        }
        String resolved = resolve(text, null);
        return resolved != null ? resolved : text;
    }

    /**
     * 按错误码解析最终对外文案
     *
     * @param code             错误码（其 message 作为最终中文兜底）
     * @param customOrNull     业务异常携带的自定义消息（可能是消息码，也可能是已写好的文案）
     * @return 当前语言下的文案；永不返回 null
     */
    public String resolveForErrorCode(ErrorCode code, String customOrNull) {
        String fallback = code == null ? null : code.getMessage();

        if (StringUtils.hasText(customOrNull) && !Objects.equals(customOrNull, fallback)) {
            // 业务层带了自定义消息：若是消息码则解析，否则原样返回（保持既有中文文案不变）
            return resolveOrKeep(customOrNull);
        }

        String key = code == null ? null : code.messageKey();
        String resolved = resolve(key, fallback);
        return resolved != null ? resolved : (fallback != null ? fallback : "请求处理失败");
    }
}
