package com.vulnaudit.util;

import org.springframework.lang.Nullable;

/**
 * 上传文件名清洗（单一事实来源）。
 *
 * <p><b>为什么必须清洗</b>：{@code MultipartFile#getOriginalFilename()} 完全由客户端控制，
 * 攻击者可以提交 {@code filename="a/../../../../etc/cron.d/x"}。若代码直接把原始文件名拼到
 * {@code Paths.get(dir).resolve(name)} 上，{@code ..} 段会在规范化后逃出上传目录，
 * 造成<b>任意路径写入</b>（覆盖配置、写 cron、覆盖 jar 等），这是典型的路径穿越（CWE-22）。
 *
 * <p><b>清洗规则</b>：只保留最后一段（剥掉任何目录部分）→ 去控制字符 → 去 Windows 保留字符 →
 * 去 {@code ..} → 去前导点（避免隐藏文件）→ 限制长度；结果为空时回退到调用方给的默认名。
 *
 * <p>注意：清洗只解决「文件名里带路径」这一层。落盘时仍应使用 <b>UUID 前缀</b>（见各上传端点），
 * 这样同名覆盖也不会发生。
 */
public final class FileNameSanitizer {

    /** 保留文件名的最大长度（字符） */
    private static final int MAX_LENGTH = 180;

    private static final String[] FALLBACKS = {"unnamed-file"};

    private FileNameSanitizer() {
    }

    /**
     * 清洗客户端提交的文件名。
     *
     * @param raw      原始文件名（可为 null）
     * @param fallback 清洗后为空时使用的默认名（null/blank 时用 {@code unnamed-file}）
     * @return 不含任何路径分隔符、不含 {@code ..} 段的文件名
     */
    public static String sanitize(@Nullable String raw, @Nullable String fallback) {
        String defaultName = isBlank(fallback) ? FALLBACKS[0] : fallback.trim();
        if (raw == null) {
            return defaultName;
        }
        // 1) 统一分隔符后只取最后一段：同时覆盖 Windows（\）与 POSIX（/）两种路径
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        // 2) 去控制字符（含 \0、\r、\n —— 会成为响应头注入的载体）
        name = name.replaceAll("\\p{Cntrl}", "");
        // 3) 去 Windows 非法字符，避免落盘行为因平台而异
        name = name.replaceAll("[:*?\"<>|]", "_");
        // 4) 去前导点：避免 `.htaccess`、`.env` 这类隐藏文件（也避免被当成上级目录）
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        // 5) 去残余 `..`（放在去前导点之后：`...htaccess` 应变成 `htaccess`，
        //    而不是先被替换成 `_` 再留下 `.htaccess`）
        while (name.contains("..")) {
            name = name.replace("..", "_");
        }
        name = name.trim();
        // 6) 截断超长名（保留扩展名所在的尾部）
        if (name.length() > MAX_LENGTH) {
            name = name.substring(name.length() - MAX_LENGTH);
        }
        return name.isBlank() ? defaultName : name;
    }

    /**
     * 文件名清洗后是否仍然「安全」（供断言/守卫使用）。
     *
     * @param name 待检查的文件名
     * @return true 表示不含路径分隔符、不含 {@code ..}、不以点开头
     */
    public static boolean isSafe(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            return false;
        }
        if (name.contains("..")) {
            return false;
        }
        return !name.startsWith(".");
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}