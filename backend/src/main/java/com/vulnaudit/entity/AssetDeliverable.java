package com.vulnaudit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_deliverables")
public class AssetDeliverable {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "asset_version_id")
    private UUID assetVersionId;

    @Column(name = "name", length = 500, nullable = false)
    private String name;

    @Column(name = "deliverable_type", length = 50, nullable = false)
    private String deliverableType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_path", length = 500, nullable = false)
    private String filePath;

    @Column(name = "checksum", length = 100)
    private String checksum;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "active";

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        log.debug("创建资产交付物: assetId={}, name={}", assetId, name);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新资产交付物: id={}, name={}", id, name);
    }
}
