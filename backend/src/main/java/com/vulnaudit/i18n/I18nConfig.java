package com.vulnaudit.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 后端 i18n 基础设施配置（t60）
 *
 * <p>资源包：{@code messages.properties}（中文默认）/ {@code messages_en.properties}（英文）。
 * <ul>
 *   <li>{@code defaultEncoding = UTF-8}：属性文件按 UTF-8 读取，中文无需转义；</li>
 *   <li>{@code fallbackToSystemLocale = false}：未知语言一律回退到默认包（中文），
 *       避免部署机 locale 为 en 时把中文默认包“翻译”成英文；</li>
 *   <li>{@code useCodeAsDefaultMessage = false}：缺码时抛 {@code NoSuchMessageException}，
 *       由 {@link ApiMessageResolver} 捕获并回退到枚举中文文案（不把原始 key 暴露给前端）。</li>
 * </ul>
 */
@Configuration
public class I18nConfig {

    public static final String MESSAGE_BASENAME = "messages";

    @Bean(name = "messageSource")
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(MESSAGE_BASENAME);
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }
}
