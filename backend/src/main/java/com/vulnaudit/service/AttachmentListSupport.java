package com.vulnaudit.service;

import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 资产附件（文件 / 交付物）列表的公共支撑（t148）
 *
 * <p>解决 t146 前端暴露的三个后端缺口里的两个：
 * <ul>
 *   <li><b>排序白名单</b>：只允许显式列出的字段，未知字段 → **400**（不能把任意属性名拼进 SQL）；</li>
 *   <li><b>上传人展示名</b>：一次 {@code userRepository.findAllById(...)} 批量解析，**禁止 N+1**。</li>
 * </ul>
 *
 * <p>分页/排序/筛选本身全部走数据库（Specification + Pageable），不做内存过滤。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentListSupport {

    /** 列表每页上限（超过 → 400，避免一次拉爆） */
    public static final int MAX_PAGE_SIZE = 100;
    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    private final UserRepository userRepository;

    /**
     * 解析排序参数（`field,dir` 或 `field`）
     *
     * @param raw       前端原始值，可空
     * @param whitelist 允许的「别名 → 实体属性」映射
     * @param fallback  未传时的默认属性
     */
    public Sort resolveSort(String raw, Map<String, String> whitelist, String fallback) {
        if (!StringUtils.hasText(raw)) {
            return Sort.by(Sort.Direction.DESC, whitelist.getOrDefault(fallback, fallback));
        }
        String[] parts = raw.split(",");
        String field = parts[0].trim();
        if (!StringUtils.hasText(field)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "sort 参数不合法: " + raw);
        }
        String property = whitelist.get(field);
        if (property == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "sort 不支持的字段: " + field + "（可选：" + String.join("/", whitelist.keySet()) + "）");
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1) {
            String dir = parts[1].trim().toLowerCase(java.util.Locale.ROOT);
            if ("asc".equals(dir)) {
                direction = Sort.Direction.ASC;
            } else if (!"desc".equals(dir)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "sort 排序方向不合法: " + parts[1].trim());
            }
        }
        return Sort.by(direction, property);
    }

    /** 备注最大长度（t148：与 description 分离的独立字段） */
    public static final int MAX_REMARK_LENGTH = 500;

    /**
     * 校验上传备注长度（null 合法；>500 → 400）
     *
     * <p>放在写库前校验，避免「文件已经落盘/落库、备注才报错」的半成功状态。
     */
    public static void validateRemark(String remark) {
        if (remark != null && remark.length() > MAX_REMARK_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "备注长度不能超过 " + MAX_REMARK_LENGTH + " 个字符，当前 " + remark.length());
        }
    }

    /** 校验并收敛每页条数（≤0 用默认值，>100 → 400） */
    public int resolveSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "size 最大 " + MAX_PAGE_SIZE + "，当前 " + size);
        }
        return size;
    }

    /** 校验页码（负数 → 0） */
    public int resolvePage(int page) {
        return Math.max(page, 0);
    }

    /**
     * 批量解析上传人展示名：userId → 用户名
     *
     * <p>**一次查询**（`findAllById`），与页大小无关；查不到的 id 不出现在结果里（前端显示空即可）。
     */
    public Map<UUID, String> uploaderNames(Collection<UUID> userIds) {
        List<UUID> ids = userIds == null ? List.of()
                : userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        userRepository.findAllById(ids).forEach(user -> {
            if (user.getId() != null) {
                names.put(user.getId(), user.getUsername());
            }
        });
        return names;
    }

    /** 文件列表的排序白名单（别名 → 实体属性） */
    public static Map<String, String> fileSortWhitelist() {
        Map<String, String> whitelist = new java.util.LinkedHashMap<>();
        whitelist.put("uploadedAt", "createdAt");
        whitelist.put("createdAt", "createdAt");
        whitelist.put("name", "name");
        whitelist.put("size", "fileSize");
        whitelist.put("fileSize", "fileSize");
        whitelist.put("category", "category");
        return whitelist;
    }

    /** 交付物列表的排序白名单（别名 → 实体属性） */
    public static Map<String, String> deliverableSortWhitelist() {
        Map<String, String> whitelist = new java.util.LinkedHashMap<>();
        whitelist.put("uploadedAt", "createdAt");
        whitelist.put("createdAt", "createdAt");
        whitelist.put("name", "name");
        whitelist.put("size", "fileSize");
        whitelist.put("fileSize", "fileSize");
        whitelist.put("type", "deliverableType");
        whitelist.put("deliverableType", "deliverableType");
        return whitelist;
    }
}
