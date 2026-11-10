package com.vulnaudit.util;

import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import jakarta.annotation.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * 列表 status 过滤的公共白名单（t162）
 *
 * <p>知识库/文档的列表接口支持 `status=DRAFT|PUBLISHED|all`：
 * <ul>
 *   <li>大小写不敏感；</li>
 *   <li>非法值 → **400**（不是静默忽略）；</li>
 *   <li>`null`/空/`all` → 返回 null，表示**不过滤**；</li>
 *   <li>返回值保持调用方语义（DRAFT/PUBLISHED 大写），由 DAO 层统一 lower() 比较。</li>
 * </ul>
 */
public final class StatusFilter {

    public static final Set<String> ALLOWED = Set.of("DRAFT", "PUBLISHED");

    private StatusFilter() {
    }

    /** @return null = 不过滤；否则 DRAFT / PUBLISHED */
    @Nullable
    public static String normalize(@Nullable String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) {
            return null;
        }
        if (!ALLOWED.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "status 不合法: " + status + "（可选：DRAFT/PUBLISHED/all）");
        }
        return normalized;
    }
}
