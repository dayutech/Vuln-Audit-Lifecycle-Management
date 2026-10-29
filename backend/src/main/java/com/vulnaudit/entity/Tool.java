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
 * 工具资源实体类
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tools")
public class Tool {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "category", length = 50, nullable = false)
    private String category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "repository_url", length = 500)
    private String repositoryUrl;

    @Column(name = "documentation_url", length = 500)
    private String documentationUrl;

    @Column(name = "install_command", columnDefinition = "TEXT")
    private String installCommand;

    @Column(name = "usage_example", columnDefinition = "TEXT")
    private String usageExample;

    @Column(name = "tags", length = 500)
    private String tags;

    /** 工具包文件名（SRS 3.5.3 中的 file_path 字段的配套信息） */
    @Column(name = "file_name", length = 300)
    private String fileName;

    /** 工具包存储路径，为空表示该工具仅有外链 */
    @Column(name = "file_path", length = 1000)
    private String filePath;

    /** 工具包大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "is_official", nullable = false)
    @Builder.Default
    private Boolean isOfficial = false;

    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private Long downloadCount = 0L;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        log.debug("创建工具资源: name={}", name);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新工具资源: id={}", id);
    }

}
