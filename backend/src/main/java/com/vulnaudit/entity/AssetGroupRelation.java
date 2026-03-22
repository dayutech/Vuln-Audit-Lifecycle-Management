package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 资产分组关联实体类
 * 
 * 功能说明：
 * - 映射数据库 asset_group_relations 表
 * - 建立资产与分组的多对多关系
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_group_relations")
@IdClass(AssetGroupRelation.AssetGroupRelationId.class)
public class AssetGroupRelation {

    @Id
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Id
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        log.debug("创建资产分组关联: assetId={}, groupId={}", assetId, groupId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssetGroupRelation that = (AssetGroupRelation) o;
        return Objects.equals(assetId, that.assetId) && Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetId, groupId);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetGroupRelationId implements Serializable {
        private UUID assetId;
        private UUID groupId;
    }
}
