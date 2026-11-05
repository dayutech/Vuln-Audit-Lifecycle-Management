package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 系统设置（t109）
 *
 * <p>键值形式的全局配置表，用于承载「由管理员统一设置、对全站生效」的参数。
 * 首个用途是登录页缺省主题 {@code login.defaultTheme}：
 * 未登录访客（登录页在认证前调用 {@code GET /api/v1/auth/login-theme}）看到的就是它；
 * 用户一旦在偏好设置里选了主题，则以其**本人偏好**（{@code users.login_theme}）为准。
 *
 * <h3>为什么用 key/value 而不是每项一列</h3>
 * 以后新增全局开关（如「强制二次验证」「默认语言」）不需要再改表结构，
 * 只加一行记录 + 一个常量键即可。
 *
 * <h3>与用户偏好的边界</h3>
 * 本表只存**缺省值**；{@code PUT /users/me/preferences} 仍只写 {@code users.login_theme}，
 * 不会把管理员缺省值写进任何用户行（t96 语义保持不变）。
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "system_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_system_settings_key", columnNames = "setting_key"))
public class SystemSetting {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 设置键（唯一，<=100） */
    @Column(name = "setting_key", length = 100, nullable = false)
    private String settingKey;

    /** 设置值（TEXT，允许为空；读取方负责校验与兜底） */
    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    /** 最后修改人（系统自愈插入时为 NULL） */
    @Column(name = "updated_by")
    private UUID updatedBy;

    /** 最后修改时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
