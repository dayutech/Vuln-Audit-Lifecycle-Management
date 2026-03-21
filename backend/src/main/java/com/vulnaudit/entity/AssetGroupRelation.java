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
public class AssetGroupRelation {

    /**
     * 资产ID
     * 外键，关联 assets 表
     */
    @Id
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    /**
     * 分组ID
     * 外键，关联 asset_groups 表
     */
    @Id
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    /**
     * 实体持久化前的回调方法
     */
    @PrePersist
    protected void onCreate() {
        log.debug("创建资产分组关联: assetId={}, groupId={}", assetId, groupId);
    }

}
