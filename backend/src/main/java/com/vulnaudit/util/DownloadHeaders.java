package com.vulnaudit.util;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 * 下载响应头工具
 *
 * <p>统一生成 {@code Content-Disposition: attachment; filename="..."; filename*=UTF-8''...}：
 * ASCII 兜底文件名 + RFC 5987 编码，兼容中文文件名与各类浏览器/下载器。
 */
public final class DownloadHeaders {

    private DownloadHeaders() {
    }

    /**
     * 构造附件下载的 Content-Disposition 值
     *
     * @param fileName 原始文件名（可为空/含中文/含引号）
     * @return 形如 {@code attachment; filename="report.pdf"; filename*=UTF-8''report.pdf}
     */
    public static String attachment(String fileName) {
        String original = (fileName == null || fileName.isBlank()) ? "attachment" : fileName;
        // 去掉 CR/LF（防响应头注入）与双引号（避免破坏 filename="..."）
        String sanitized = original.replace("\r", "").replace("\n", "").replace("\"", "'");

        StringBuilder ascii = new StringBuilder(sanitized.length());
        for (char c : sanitized.toCharArray()) {
            ascii.append(c > 31 && c < 127 ? c : '_');
        }
        String encoded = URLEncoder.encode(sanitized, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
