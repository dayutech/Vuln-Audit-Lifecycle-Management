package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 资产应用元数据历史实体类
 * 
 * 功能说明：
 * - 映射数据库 asset_app_metadata_history 表
 * - 记录元数据变更历史
 * - 支持审计追溯
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_app_metadata_history")
public class AssetAppMetadataHistory {

    /**
     * 历史记录主键ID
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 元数据ID
     * 外键，关联 asset_app_metadata 表
     */
    @Column(name = "metadata_id", nullable = false)
    private UUID metadataId;

    /**
     * 版本号
     */
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 修改人ID
     * 外键，关联 users 表
     */
    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    /**
     * 修改理由（必填）
     * 最大长度500字符
     */
    @Column(name = "change_reason", length = 500, nullable = false)
    private String changeReason;

    /**
     * 系统架构
     */
    @Column(name = "system_architecture", length = 50)
    private String systemArchitecture;

    /**
     * 安装路径
     */
    @Column(name = "install_path", length = 500)
    private String installPath;

    /**
     * 启动脚本位置
     */
    @Column(name = "startup_script_path", length = 500)
    private String startupScriptPath;

    /**
     * 配置文件位置
     */
    @Column(name = "config_file_path", length = 500)
    private String configFilePath;

    /**
     * 启动命令
     */
    @Column(name = "startup_command", length = 1000)
    private String startupCommand;

    /**
     * 采用语言
     */
    @Column(name = "programming_language", length = 100)
    private String programmingLanguage;

    /**
     * 使用框架
     */
    @Column(name = "framework", length = 100)
    private String framework;

    /**
     * 数据库类型
     */
    @Column(name = "database_type", length = 50)
    private String databaseType;

    /**
     * 数据库连接信息
     */
    @Column(name = "database_connection", length = 500)
    private String databaseConnection;

    /**
     * 管理员用户名
     */
    @Column(name = "admin_username", length = 100)
    private String adminUsername;

    /**
     * 管理员密码（加密存储）
     */
    @Column(name = "admin_password_encrypted", length = 255)
    private String adminPasswordEncrypted;

    /**
     * 服务端口
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
     * 实体持久化前的回调方法
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        log.debug("创建元数据历史: metadataId={}, version={}", metadataId, version);
    }

}
