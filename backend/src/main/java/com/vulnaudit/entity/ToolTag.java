package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

/**
 * 工具标签实体类
 * 
 * 功能说明：
 * - 为工具添加标签分类
 * - 支持多维度标签检索
 * - 便于工具分类管理
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tool_tags")
public class ToolTag {

    /**
     * 主键ID，自动生成UUID
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 外键，关联tools表
     */
    @Column(name = "tool_id", nullable = false)
    private UUID toolId;

    /**
     * 标签名称
     */
    @Column(name = "tag", nullable = false, length = 50)
    private String tag;

    /**
     * 持久化前的生命周期回调
     */
    @PrePersist
    protected void onCreate() {
        log.debug("创建工具标签: toolId={}, tag={}", toolId, tag);
    }
}
