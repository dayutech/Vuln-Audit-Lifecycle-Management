package com.vulnaudit.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户实体类
 * 
 * 功能说明：
 * - 映射数据库 users 表
 * - 存储用户基本信息和认证信息
 * - 支持用户状态管理（活跃/暂停/停用）
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {

    /**
     * 用户主键ID
     * 使用UUID自动生成，确保全局唯一性
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 状态：正常（既有值，禁止改名） */
    public static final String STATUS_ACTIVE = "active";

    /** 状态：暂停（既有值，禁止改名） */
    public static final String STATUS_SUSPENDED = "suspended";

    /** 状态：停用/软删除（既有值，禁止改名） */
    public static final String STATUS_DEACTIVATED = "deactivated";

    /** 状态：待管理员审批（t102 追加，注册占位用户） */
    public static final String STATUS_PENDING_APPROVAL = "pending_approval";

    /** 状态：注册申请已被拒绝（t102 追加） */
    public static final String STATUS_REJECTED = "rejected";

    /**
     * 用户名
     * 用于系统登录，全局唯一
     * 最大长度50字符
     */
    @Column(name = "username", length = 50, unique = true, nullable = false)
    private String username;

    /**
     * 邮箱地址
     * 用于接收系统通知，全局唯一
     * 最大长度255字符
     */
    @Column(name = "email", length = 255, unique = true, nullable = false)
    private String email;

    /**
     * 手机号
     * 用于短信通知 / 自助注册（t102 起唯一，可空——历史用户允许为空）
     * 最大长度20字符
     */
    @Column(name = "phone", length = 20, unique = true)
    private String phone;

    /**
     * 手机号是否已验证（t102）
     *
     * <p>只有通过验证码校验或管理员审批通过的注册申请才会置 true；
     * 历史用户与管理员代建的账号为 false。
     */
    @lombok.Builder.Default
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /**
     * 手机号是否已验证（t102）
     */
    @lombok.Builder.Default
    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    /**
     * 密码哈希值
     * 使用BCrypt算法加密存储
     * 最大长度255字符
     *
     * <p><b>安全约定</b>：该字段永不参与 JSON 序列化/反序列化（{@link JsonIgnore}）。
     * 需要「不返回密码」时不要修改实体的字段值（那会污染受管实体并触发 NOT NULL 约束），
     * 而是靠这里的序列化忽略；如需自定义响应结构请用 DTO。
     */
    @JsonIgnore
    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    /**
     * 真实姓名
     * 用于显示和业务沟通
     * 最大长度100字符
     */
    @Column(name = "full_name", length = 100, nullable = false)
    private String fullName;

    /**
     * 头像URL
     * 用户头像图片地址，可选字段
     * 最大长度500字符
     */
    @Column(name = "avatar", length = 500)
    private String avatar;

    /**
     * 职位
     * 用户在组织中的职位，可选字段
     * 最大长度100字符
     */
    @Column(name = "position", length = 100)
    private String position;

    /**
     * 登录页主题偏好（t96 / t100 / t112）：8 套主题见 {@link com.vulnaudit.entity.enums.LoginTheme}。
     *
     * <p><b>三态语义</b>（t112）：
     * <ul>
     *   <li>{@code null} = <b>跟随系统默认</b>（admin 在 {@code system_settings.login.defaultTheme} 设置的值）——
     *       不再把缺省值固化进用户行，管理员之后改缺省，这类用户自动跟随；</li>
     *   <li>具体 code（pencil/…） = 用户**亲自指定**的主题，管理员改缺省不影响它；</li>
     *   <li>历史行若已是具体值（t96 建表时的 {@code 'pencil'}），保持不变，视为用户指定（不迁移）。</li>
     * </ul>
     *
     * <p>DB 列 {@code login_theme VARCHAR(32) NULL}（V19 建立 + V23 去掉 NOT NULL）；
     * 写入前由服务层按 {@code LoginTheme} 白名单校验，非法值不会落库。
     */
    @Column(name = "login_theme", length = 32)
    private String loginTheme;

    /**
     * 是否「跟随系统默认」：{@code login_theme IS NULL} 即跟随（t112）。
     *
     * <p>只用于 JSON 输出（{@code /auth/me}、{@code /users} 等），不落库；
     * 前端可据此区分「null 表示跟随缺省」与「字段缺失/序列化异常」。
     */
    public boolean isFollowDefault() {
        return this.loginTheme == null;
    }

    /**
     * 用户状态
     * 可选值：active(活跃)/suspended(暂停)/deactivated(停用)
     * 默认值为 active
     */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "active";

    /**
     * token 失效时间戳（t103）
     *
     * <p>忘记密码重置成功后写入当前时间：{@code JwtAuthenticationFilter} 会拒绝
     * 「签发时间（iat）早于此时间戳」的 token，从而让该用户所有已签发 token 立即失效。
     *
     * <p>当前是无状态 JWT，没有 token 黑名单；用「用户级时间戳 + iat 比较」实现全端下线，
     * 代价是「与重置同一秒内签发的 token」仍然有效（≤1 秒窗口，已在 README §49 记录）。
     */
    @Column(name = "token_invalid_before")
    private LocalDateTime tokenInvalidBefore;

    /**
     * 创建时间
     * 记录用户账号创建的时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * 记录用户信息最后更新的时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 最后登录时间
     * 记录用户最后一次成功登录的时间，可选字段
     */
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /** 是否已开启双因子认证（t124；只有 confirm 通过后才为 true） */
    @Column(name = "two_factor_enabled", nullable = false)
    @Builder.Default
    private boolean twoFactorEnabled = false;

    /**
     * TOTP 密钥（t124）：Base32 经 AES-GCM 加密后的密文（{@code v1:base64(iv[12] || ct || tag)}）。
     * <b>绝不落库明文</b>；未配置主密钥时该列保持 null 且 2FA 功能整体禁用。
     *
     * <p>长度说明（t125 修正）：32 字符 Base32 明文 + 12 字节 IV + 16 字节 tag = 60 字节，
     * Base64 后 80 字符，加 {@code v1:} 前缀实为 <b>83 字符</b>；原设计写死 64 会触发
     * {@code Value too long for column "totp_secret"}（H2/MariaDB 同），因此列宽取 128 留余量。
     */
    @Column(name = "totp_secret", length = 128)
    private String totpSecret;

    /** 双因子确认时间（t124） */
    @Column(name = "two_factor_confirmed_at")
    private LocalDateTime twoFactorConfirmedAt;

    /**
     * 实体持久化前的回调方法
     * 自动设置创建时间和更新时间
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        log.debug("创建用户实体: username={}, email={}", username, email);
    }

    /**
     * 实体更新前的回调方法
     * 自动更新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新用户实体: id={}, username={}", id, username);
    }

    /**
     * 检查用户是否处于活跃状态
     * 
     * @return 如果用户状态为active返回true，否则返回false
     */
    public boolean isActive() {
        return "active".equals(this.status);
    }

    /**
     * 检查用户是否被暂停
     * 
     * @return 如果用户状态为suspended返回true，否则返回false
     */
    public boolean isSuspended() {
        return "suspended".equals(this.status);
    }

    /**
     * 检查用户是否被停用
     * 
     * @return 如果用户状态为deactivated返回true，否则返回false
     */
    public boolean isDeactivated() {
        return "deactivated".equals(this.status);
    }

    /**
     * 是否「待管理员审批」（t102 自助注册）
     *
     * <p>注册时即落一条该状态的占位用户（不可登录），管理员审批通过后转为 {@link #STATUS_ACTIVE}。
     */
    public boolean isPendingApproval() {
        return STATUS_PENDING_APPROVAL.equals(this.status);
    }

    /**
     * 是否「注册申请已被拒绝」（t102 自助注册）
     */
    public boolean isRejected() {
        return STATUS_REJECTED.equals(this.status);
    }

}
