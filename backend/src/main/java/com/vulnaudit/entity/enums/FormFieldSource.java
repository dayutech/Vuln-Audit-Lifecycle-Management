package com.vulnaudit.entity.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * 表单字段的「库字段来源」白名单（t178，单一事实来源）
 *
 * <p>`source_type` 非空 = **库字段**（生成实例时从业务库取快照）；为空 = 手填字段。
 *
 * <p>命名沿用 `主体.属性`：`CUSTOMER.NAME`、`ASSET.STATUS` …… 好处是前端/导出都能直接按点号拆解，
 * 且新增主体时不用改协议。
 */
public enum FormFieldSource {

    CUSTOMER_NAME("CUSTOMER.NAME", "客户名称", "CUSTOMER", "name"),
    CUSTOMER_CODE("CUSTOMER.CODE", "客户编号", "CUSTOMER", "code"),
    CUSTOMER_INDUSTRY("CUSTOMER.INDUSTRY", "所属行业", "CUSTOMER", "industry"),
    PROJECT_NAME("PROJECT.NAME", "项目名称", "PROJECT", "name"),
    PROJECT_CODE("PROJECT.CODE", "项目编号", "PROJECT", "code"),
    PROJECT_TYPE("PROJECT.TYPE", "项目类型", "PROJECT", "type"),
    ASSET_NAME("ASSET.NAME", "资产名称", "ASSET", "name"),
    ASSET_CODE("ASSET.CODE", "资产编号", "ASSET", "code"),
    ASSET_TARGET_TYPE("ASSET.TARGET_TYPE", "资产类型", "ASSET", "targetType"),
    ASSET_PRIORITY("ASSET.PRIORITY", "资产优先级", "ASSET", "priority"),
    ASSET_STATUS("ASSET.STATUS", "资产状态", "ASSET", "status"),
    TEAM_NAME("TEAM.NAME", "团队名称", "TEAM", "name"),
    USER_FULL_NAME("USER.FULL_NAME", "用户姓名", "USER", "fullName");

    private final String code;
    private final String label;
    private final String subject;
    private final String property;

    FormFieldSource(String code, String label, String subject, String property) {
        this.code = code;
        this.label = label;
        this.subject = subject;
        this.property = property;
    }

    /** 存进 DB 的值（`CUSTOMER.NAME` 这种） */
    public String getCode() {
        return code;
    }

    /** 中文显示名（前端下拉/导出表头用） */
    public String getLabel() {
        return label;
    }

    /** 主体（CUSTOMER/PROJECT/ASSET/TEAM/USER） */
    public String getSubject() {
        return subject;
    }

    /** 属性名（实体字段名） */
    public String getProperty() {
        return property;
    }

    /** code → 枚举（大小写不敏感；未知返回 empty，由调用方转 400） */
    public static Optional<FormFieldSource> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.code.equals(normalized)).findFirst();
    }

    /** 白名单校验：非法抛 IllegalArgumentException（service 转 400，附可选值） */
    public static FormFieldSource require(String code) {
        return fromCode(code).orElseThrow(() -> new IllegalArgumentException(
                "source_type 不合法: " + code + "（可选：" + String.join("/", codes()) + "）"));
    }

    public static java.util.List<String> codes() {
        return Arrays.stream(values()).map(FormFieldSource::getCode).toList();
    }

    /**
     * 从业务实体取值（生成实例快照时使用，t179）
     *
     * <p>规则：对象为 null 或字段为空 → 返回 null（**不抛异常、不阻塞生成**）；
     * 传入的对象类型与来源主体不匹配 → 返回 null（调用方已记 WARN）。
     *
     * @param subject CUSTOMER/PROJECT/ASSET/TEAM/USER 实体实例
     * @return 取值字符串（`null` 表示该字段在业务库里为空）
     */
    public String resolve(Object subject) {
        if (subject == null) {
            return null;
        }
        // Java 17：用 instanceof 链（pattern switch 需要 --enable-preview）
        Object value = null;
        if (subject instanceof com.vulnaudit.entity.Customer customer) {
            value = customerValue(customer);
        } else if (subject instanceof com.vulnaudit.entity.Project project) {
            value = projectValue(project);
        } else if (subject instanceof com.vulnaudit.entity.Asset asset) {
            value = assetValue(asset);
        } else if (subject instanceof com.vulnaudit.entity.Team team) {
            value = teamValue(team);
        } else if (subject instanceof com.vulnaudit.entity.User user) {
            value = userValue(user);
        }
        return value == null ? null : String.valueOf(value);
    }

    private Object customerValue(com.vulnaudit.entity.Customer customer) {
        return switch (this) {
            case CUSTOMER_NAME -> customer.getName();
            case CUSTOMER_CODE -> customer.getCode();
            case CUSTOMER_INDUSTRY -> customer.getIndustry();
            default -> null;
        };
    }

    private Object projectValue(com.vulnaudit.entity.Project project) {
        return switch (this) {
            case PROJECT_NAME -> project.getName();
            case PROJECT_CODE -> project.getCode();
            case PROJECT_TYPE -> project.getType();
            default -> null;
        };
    }

    private Object assetValue(com.vulnaudit.entity.Asset asset) {
        return switch (this) {
            case ASSET_NAME -> asset.getName();
            case ASSET_CODE -> asset.getCode();
            case ASSET_TARGET_TYPE -> asset.getTargetType();
            case ASSET_PRIORITY -> asset.getPriority();
            case ASSET_STATUS -> asset.getStatus();
            default -> null;
        };
    }

    private Object teamValue(com.vulnaudit.entity.Team team) {
        return this == TEAM_NAME ? team.getName() : null;
    }

    private Object userValue(com.vulnaudit.entity.User user) {
        if (this != USER_FULL_NAME) {
            return null;
        }
        // 姓名优先，回落到账号（避免「姓名未填」时快照全空）
        return user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName() : user.getUsername();
    }
}
