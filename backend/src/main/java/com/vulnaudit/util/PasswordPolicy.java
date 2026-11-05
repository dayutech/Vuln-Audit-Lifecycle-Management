package com.vulnaudit.util;

import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;

import java.util.regex.Pattern;

/**
 * 密码强度策略（t103，单一实现）
 *
 * <p>规则：长度 8–64，且在「大写字母 / 小写字母 / 数字 / 符号」四类中<b>至少满足三类</b>。
 *
 * <p><b>为什么不散落在各处</b>：忘记密码、未来的强制改密、后台重置都需要同一套规则；
 * 复制多份必然漂移，因此统一收敛到本类，任何新入口调用 {@link #validate(String)} 即可。
 *
 * <p><b>与既有接口的关系</b>：{@code /auth/change-password} 与管理员
 * {@code /users/{id}/reset-password} 历史上只做长度校验（6–100），收紧会改变既有契约并影响
 * 已发布用例的取值，因此本轮**只对新的忘记密码链路启用**该策略；对齐计划见 README §49.7 / 报告 §8。
 */
public final class PasswordPolicy {

    /** 最小长度 */
    public static final int MIN_LENGTH = 8;

    /** 最大长度（与 BCrypt 72 字节上限留出余量） */
    public static final int MAX_LENGTH = 64;

    /** 至少需要满足的字符类别数 */
    public static final int MIN_CLASSES = 3;

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9]");

    /** 统一的提示文案（中文默认；调用方可自行包一层 i18n 消息码） */
    public static final String REQUIREMENT_MESSAGE =
            "密码强度不足：至少 " + MIN_LENGTH + " 位，且需包含大写字母、小写字母、数字、符号中的至少三类";

    private PasswordPolicy() {
    }

    /**
     * 校验密码强度；不满足抛 400
     *
     * @param raw 明文密码
     * @throws BusinessException 强度不足
     */
    public static void validate(String raw) {
        if (!isStrong(raw)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, REQUIREMENT_MESSAGE);
        }
    }

    /**
     * 是否满足强度要求（不做抛异常，供需要布尔判定的场景使用）
     */
    public static boolean isStrong(String raw) {
        if (raw == null) {
            return false;
        }
        if (raw.length() < MIN_LENGTH || raw.length() > MAX_LENGTH) {
            return false;
        }
        int classes = 0;
        if (UPPER.matcher(raw).find()) {
            classes++;
        }
        if (LOWER.matcher(raw).find()) {
            classes++;
        }
        if (DIGIT.matcher(raw).find()) {
            classes++;
        }
        if (SYMBOL.matcher(raw).find()) {
            classes++;
        }
        return classes >= MIN_CLASSES;
    }
}
