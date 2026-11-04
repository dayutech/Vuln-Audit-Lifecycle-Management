package com.vulnaudit.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 登录页主题枚举（t96 / t100）
 *
 * <p><b>单一事实来源</b>：登录页可选主题只在枚举里声明一次，以下位置全部从这里派生，不再各写一份字符串列表：
 * <ul>
 *   <li>服务层白名单校验（{@link #isValid(String)} / {@link #fromCode(String)}）；</li>
 *   <li>默认值（{@link #DEFAULT} = {@code pencil}）；</li>
 *   <li>非法值错误消息里的「支持: …」列表（{@link #supportedCodes()}）；</li>
 *   <li>单测中的合法值遍历与一致性断言（{@link #codes()}）。</li>
 * </ul>
 *
 * <p><b>编码约定</b>：{@code code} 即前后端与数据库共同使用的值（小写、必要处用连字符），
 * 与前端 {@code frontend/src/components/login/themes.ts} 的 {@code LoginThemeEntry.id} 一一对应。
 * 注意 {@code mono-grid} 含连字符，**不是** {@code mono_grid}。
 *
 * <p>加主题时只改本枚举 + 前端注册表；{@code UserPreferenceTest#enumAndWhitelistStayInSync}
 * 会守住「枚举 / 服务层白名单 / 默认值 / 错误消息列表」四处不脱节。
 */
@Getter
@AllArgsConstructor
public enum LoginTheme {

    /** 铅笔手绘角色（默认） */
    PENCIL("pencil"),

    /** 几何拼贴角色 */
    GEOMETRIC("geometric"),

    /** 猫头鹰角色 */
    OWL("owl"),

    /** 像素眼睛角色 */
    PIXEL_EYES("pixel-eyes"),

    /** 极光（抽象极简，t99） */
    AURORA("aurora"),

    /** 单色栅格（抽象极简，t99） */
    MONO_GRID("mono-grid"),

    /** 轨道（抽象极简，t99） */
    ORBIT("orbit"),

    /** 超大字号（抽象极简，t99） */
    BIG_TYPE("big-type");

    /** 默认主题：既有用户与新建用户都落在这里，不随主题扩展而变化 */
    public static final LoginTheme DEFAULT = PENCIL;

    /** 与 DB 列 {@code users.login_theme VARCHAR(32)} 长度对齐，供 DTO 校验文档引用 */
    public static final int MAX_CODE_LENGTH = 32;

    /** 落库/传输用的主题代码 */
    private final String code;

    /**
     * 全部主题代码（枚举声明顺序，只读）
     */
    public static List<String> codes() {
        return Stream.of(values()).map(LoginTheme::getCode).toList();
    }

    /**
     * 「支持: a, b, c」中的主题列表部分（逗号+空格分隔，顺序稳定）
     */
    public static String supportedCodes() {
        return String.join(", ", codes());
    }

    /**
     * 归一化：去首尾空格 + 转小写；空白返回 {@code null}
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * 是否受支持（大小写与首尾空格不敏感）
     */
    public static boolean isValid(String raw) {
        return fromCode(raw) != null;
    }

    /**
     * 按代码取枚举；非法/空白返回 {@code null}
     */
    public static LoginTheme fromCode(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) {
            return null;
        }
        for (LoginTheme theme : values()) {
            if (theme.code.equals(normalized)) {
                return theme;
            }
        }
        return null;
    }

    /**
     * 按代码取枚举；非法/空白回落到 {@link #DEFAULT}（读取历史脏数据用，不抛异常）
     */
    public static LoginTheme fromCodeOrDefault(String raw) {
        LoginTheme theme = fromCode(raw);
        return theme != null ? theme : DEFAULT;
    }

    /**
     * 可选的枚举（供需要区分「非法」与「默认」的调用方使用）
     */
    public static Optional<LoginTheme> optional(String raw) {
        return Optional.ofNullable(fromCode(raw));
    }

    /**
     * 便于日志/文档输出：{@code pencil}
     */
    @Override
    public String toString() {
        return code;
    }
}
