package com.vulnaudit.service.impl;

import com.vulnaudit.entity.AssetGroup;
import com.vulnaudit.entity.AssetGroupRelation;
import com.vulnaudit.entity.TagDefinition;
import com.vulnaudit.repository.AssetGroupRelationRepository;
import com.vulnaudit.repository.AssetGroupRepository;
import com.vulnaudit.repository.AssetTagRepository;
import com.vulnaudit.repository.TagDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 资产分组 → 标签的迁移器（t141，与 V30 SQL 语义一致、幂等）
 *
 * <p>生产一次性迁移走 {@code V30__asset_groups_to_tags.sql}；本类提供**同一套规则**的 Java 实现，
 * 用于：① 单测可直接驱动迁移语义（H2 不执行迁移脚本）；② 运维可重复执行（只补缺，不重复建）。
 *
 * <p>规则：未软删且未迁过的分组 → 一条 {@code tag_definitions}（name=分组名，builtin=false）；
 * 其 {@code asset_group_relations} → {@code asset_tags}（同名去重）；最后写 {@code migrated_at}。
 * **不删旧数据**。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupToTagMigrator {

    private static final UUID SYSTEM_USER = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final AssetGroupRepository groupRepository;
    private final AssetGroupRelationRepository relationRepository;
    private final TagDefinitionRepository tagDefinitionRepository;
    private final AssetTagRepository assetTagRepository;

    /** 迁移结果（供测试与日志） */
    public record Result(long definitionsCreated, long linksCreated, long groupsMarked) {
    }

    @Transactional
    public Result migrateOnce() {
        List<AssetGroup> pending = groupRepository.findAll().stream()
                .filter(group -> group.getDeletedAt() == null)
                .filter(group -> group.getMigratedAt() == null)
                .toList();

        long definitionsCreated = 0;
        long linksCreated = 0;
        for (AssetGroup group : pending) {
            if (tagDefinitionRepository.findByName(group.getName()).isEmpty()) {
                tagDefinitionRepository.save(TagDefinition.builder()
                        .name(group.getName())
                        .builtin(false)
                        .sortOrder(100)
                        .build());
                definitionsCreated++;
            }
            for (AssetGroupRelation relation : relationRepository.findByGroupId(group.getId())) {
                UUID assetId = relation.getAssetId();
                if (assetId == null) {
                    continue;
                }
                boolean exists = assetTagRepository.findByAssetId(assetId).stream()
                        .anyMatch(tag -> group.getName().equals(tag.getTag()));
                if (exists) {
                    continue;
                }
                assetTagRepository.save(com.vulnaudit.entity.AssetTag.builder()
                        .assetId(assetId)
                        .tag(group.getName())
                        .createdBy(SYSTEM_USER)
                        .build());
                linksCreated++;
            }
            group.setMigratedAt(LocalDateTime.now());
            groupRepository.save(group);
        }
        log.info("分组→标签迁移完成: definitionsCreated={}, linksCreated={}, groupsMarked={}",
                definitionsCreated, linksCreated, pending.size());
        return new Result(definitionsCreated, linksCreated, pending.size());
    }
}
