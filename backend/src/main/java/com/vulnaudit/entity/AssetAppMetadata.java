package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 资产应用元数据实体类
 * 
 * 功能说明：
 * - 映射数据库 asset_app_metadata 表
 * - 存资产应用配置信息
 * - 支持版本管理
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_app_metadata")
public class AssetAppMetadata {

    /**
     * 元数据主键ID
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 资产ID
     * 外键，关联 assets 表
     */
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    /**
     * 认领人ID
     * 外键，关联 users 表
     */
    @Column(name = "claimed_by", nullable = false)
    private UUID claimedBy;

    /**
     * 当前版本号
     * 默认值为1
     */
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    /**
     * 系统架构
     * 可选值：x86/arm/mips等
     * 最大长度50字符
     */
    @Column(name = "system_architecture", length = 50)
    private String systemArchitecture;

    /**
     * 安装路径
     * 最大长度500字符
     */
    @Column(name = "install_path", length = 500)
    private String installPath;

    /**
     * 启动脚本位置
     * 最大长度500字符
     */
    @Column(name = "startup_script_path", length = 500)
    private String startupScriptPath;

    /**
     * 配置文件位置
     * 最大长度500字符
     */
    @Column(name = "config_file_path", length = 500)
    private String configFilePath;

    /**
     * 启动命令
     * 最大长度1000字符
     */
    @Column(name = "startup_command", length = 1000)
    private String startupCommand;

    /**
     * 采用语言
     * 最大长度100字符
     */
    @Column(name = "programming_language", length = 100)
    private String programmingLanguage;

    /**
     * 使用框架
     * 最大长度100字符
     */
    @Column(name = "framework", length = 100)
    private String framework;

    /**
     * 数据库类型
     * 最大长度50字符
     */
    @Column(name = "database_type", length = 50)
    private String databaseType;

    /**
     * 数据库连接信息
     * 仅存储连接字符串模板
     * 最大长度500字符
     */
    @Column(name = "database_connection", length = 500)
    private String databaseConnection;

    /**
     * 管理员用户名
     * 最大长度100字符
     */
    @Column(name = "admin_username", length = 100)
    private String adminUsername;

    /**
     * 管理员密码（加密存储）
     * 最大长度255字符
     */
    @Column(name = "admin_password_encrypted", length = 255)
    private String adminPasswordEncrypted;

    /**
     * 服务端口
     * 多个端口用逗号分隔
     * 最大长度200字符
     */
    @Column(name = "service_ports", length = 200)
    private String servicePorts;

    /**
     * API端点列表
     */
    @Column(name = "api_endpoints", columnDefinition = "TEXT")
    private String apiEndpoints;

    /**
     * 特殊说明
     */
    @Column(name = "special_notes", columnDefinition = "TEXT")
    private String specialNotes;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 实体持久化前的回调方法
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        log.debug("创建资产应用元数据: assetId={}", assetId);
    }

    /**
     * 实体更新前的回调方法
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新资产应用元数据: id={}", id);
    }

}
