package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.TagDefinitionUpsertRequest;
import com.vulnaudit.dto.TagDefinitionVO;
import com.vulnaudit.dto.TagOptionVO;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetTag;
import com.vulnaudit.entity.TagDefinition;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AssetTagRepository;
import com.vulnaudit.repository.TagDefinitionRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.TagDefinitionService;
import com.vulnaudit.util.Paging;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 标签定义实现（t141）
 *
 * <p>标签关联沿用既有 {@code asset_tags(asset_id, tag)}：标签定义负责「目录 + 内置保护 + 软删」，
 * 关联行负责「哪个资产打了哪个标签」，因此自动标签与手工标签天然同表、天然去重。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagDefinitionServiceImpl implements TagDefinitionService {

    private static final String AUDIT_ACTION = "UPDATE_TAG_DEFINITION";

    private final TagDefinitionRepository tagDefinitionRepository;
    private final AssetTagRepository assetTagRepository;
    private final AssetRepository assetRepository;
    private final AuditLogService auditLogService;

    // ------------------------------------------------------------------ 内置

    @Override
    @Transactional
    public boolean ensureBuiltin() {
        if (tagDefinitionRepository.count() > 0) {
            return false;
        }
        int order = 0;
        for (String name : BUILTIN_TAGS) {
            tagDefinitionRepository.save(TagDefinition.builder()
                    .name(name)
                    .builtin(true)
                    .sortOrder(order++)
                    .build());
        }
        log.info("已初始化内置标签: count={}", BUILTIN_TAGS.size());
        return true;
    }

    // ------------------------------------------------------------------ 下拉

    @Override
    public List<TagOptionVO> options(@Nullable Set<UUID> visibleAssetIds) {
        List<TagDefinition> definitions = tagDefinitionRepository
                .findByDeletedAtIsNullOrderByBuiltinDescSortOrderAscNameAsc();
        if (definitions.isEmpty()) {
            return List.of();
        }
        // 一次取全部关联行，按标签名分组后按可见范围收敛（不做 per-tag 查询）
        List<AssetTag> allTags = assetTagRepository.findAll();
        Set<UUID> alive = aliveAssetIds(allTags.stream().map(AssetTag::getAssetId).distinct().toList());
        List<TagOptionVO> result = new ArrayList<>(definitions.size());
        for (TagDefinition definition : definitions) {
            Set<UUID> assetIds = new HashSet<>();
            for (AssetTag tag : allTags) {
                if (!definition.getName().equals(tag.getTag())) {
                    continue;
                }
                UUID assetId = tag.getAssetId();
                if (assetId == null || !alive.contains(assetId)) {
                    continue;
                }
                if (visibleAssetIds != null && !visibleAssetIds.contains(assetId)) {
                    continue;
                }
                assetIds.add(assetId);
            }
            result.add(new TagOptionVO(definition.getId(), definition.getName(), definition.getColor(),
                    definition.isBuiltin(), (long) assetIds.size()));
        }
        return result;
    }

    /** 存活资产（软删资产不算；t140 的 @SQLRestriction 已过滤） */
    private Set<UUID> aliveAssetIds(Collection<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return Set.of();
        }
        return assetRepository.findAllById(assetIds).stream().map(Asset::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ------------------------------------------------------------------ 管理员

    @Override
    public PageResponse<TagDefinitionVO> adminList(@Nullable String keyword, boolean includeDeleted,
                                                   int page, int size) {
        int safePage = Paging.page(page);
        int safeSize = Paging.size(size);
        String kw = keyword == null ? "" : keyword.trim();
        Page<TagDefinition> result = tagDefinitionRepository.searchAdmin(kw, includeDeleted,
                PageRequest.of(safePage, safeSize));
        List<TagDefinition> content = includeDeleted
                ? result.getContent()
                : result.getContent().stream().filter(t -> !t.isDeleted()).toList();
        return PageResponse.<TagDefinitionVO>builder()
                .content(content.stream().map(this::toVO).toList())
                .totalElements((long) content.size())
                .totalPages(result.getTotalPages())
                .currentPage(safePage)
                .pageSize(safeSize)
                .build();
    }

    @Override
    @Transactional
    public TagDefinitionVO create(TagDefinitionUpsertRequest request, @Nullable UUID operatorId) {
        request.requireCreateFields();
        String name = request.name().trim();
        // 唯一性含软删行：软删后同名也不可复用（与 customers/projects 的唯一键口径一致）
        if (tagDefinitionRepository.countByName(name) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "标签已存在: " + name);
        }
        TagDefinition saved = tagDefinitionRepository.save(TagDefinition.builder()
                .name(name)
                .color(StringUtils.hasText(request.color()) ? request.color().trim() : null)
                .builtin(false)
                .sortOrder(request.sortOrder() == null ? 100 : request.sortOrder())
                .build());
        audit(operatorId, saved, "新建标签: name=" + saved.getName());
        return toVO(saved);
    }

    @Override
    @Transactional
    public TagDefinitionVO update(UUID id, TagDefinitionUpsertRequest request, @Nullable UUID operatorId) {
        TagDefinition definition = requireActive(id);
        boolean nameChanged = StringUtils.hasText(request.name())
                && !request.name().trim().equals(definition.getName());
        if (definition.isBuiltin()) {
            if (nameChanged) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "内置标签不可修改名称");
            }
        } else if (nameChanged) {
            String name = request.name().trim();
            TagDefinition existing = tagDefinitionRepository.findByName(name).orElse(null);
            if (existing != null && !existing.getId().equals(id)) {
                throw new BusinessException(ErrorCode.CONFLICT, "标签已存在: " + name);
            }
            definition.setName(name);
        }
        if (request.color() != null) {
            definition.setColor(StringUtils.hasText(request.color()) ? request.color().trim() : null);
        }
        if (request.sortOrder() != null) {
            definition.setSortOrder(request.sortOrder());
        }
        TagDefinition saved = tagDefinitionRepository.save(definition);
        audit(operatorId, saved, "更新标签: name=" + saved.getName()
                + ", color=" + saved.getColor() + ", sortOrder=" + saved.getSortOrder());
        return toVO(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id, @Nullable UUID operatorId) {
        TagDefinition definition = requireActive(id);
        if (definition.isBuiltin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "内置标签不可删除");
        }
        long used = assetTagRepository.findByTag(definition.getName()).size();
        if (used > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "已有 " + used + " 个资产使用该标签，请先取消标签");
        }
        definition.setDeletedAt(LocalDateTime.now());
        tagDefinitionRepository.save(definition);
        audit(operatorId, definition, "删除标签（软删）: name=" + definition.getName());
        log.info("软删标签定义: name={}, id={}", definition.getName(), id);
    }

    private TagDefinition requireActive(UUID id) {
        return tagDefinitionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "标签不存在"));
    }

    private void audit(@Nullable UUID operatorId, TagDefinition definition, String detail) {
        auditLogService.log(operatorId, AUDIT_ACTION, "tag_definition", definition.getId(), detail);
    }

    private TagDefinitionVO toVO(TagDefinition definition) {
        return new TagDefinitionVO(definition.getId(), definition.getName(), definition.getColor(),
                definition.isBuiltin(), definition.getSortOrder(), definition.getDeletedAt());
    }

    // ------------------------------------------------------------------ 自动标签

    @Override
    @Transactional
    public int applyTags(UUID assetId, List<String> tagNames, @Nullable UUID operatorId) {
        if (assetId == null || tagNames == null || tagNames.isEmpty()) {
            return 0;
        }
        Set<String> existing = new LinkedHashSet<>(assetTagRepository.findTagsByAssetId(assetId));
        int added = 0;
        for (String raw : tagNames) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String name = raw.trim();
            if (name.length() > 50) {
                name = name.substring(0, 50);
            }
            if (existing.contains(name)) {
                continue;
            }
            // 定义不存在 → 自动创建为自定义标签（builtin=false）
            final String finalName = name;
            tagDefinitionRepository.findByName(finalName).orElseGet(() -> tagDefinitionRepository.save(
                    TagDefinition.builder().name(finalName).builtin(false).sortOrder(1000).build()));
            assetTagRepository.save(AssetTag.builder()
                    .assetId(assetId)
                    .tag(name)
                    .createdBy(operatorId != null ? operatorId
                            : UUID.fromString("00000000-0000-0000-0000-000000000000"))
                    .build());
            existing.add(name);
            added++;
        }
        if (added > 0) {
            log.info("自动打标签: assetId={}, added={}", assetId, added);
        }
        return added;
    }

    @Override
    public Set<UUID> resolveAssetIdsByTags(@Nullable Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        Set<UUID> result = null;
        for (String raw : tags) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            Set<UUID> ids = new HashSet<>();
            for (AssetTag tag : assetTagRepository.findByTag(raw.trim())) {
                if (tag.getAssetId() != null) {
                    ids.add(tag.getAssetId());
                }
            }
            // AND 语义：逐个求交；某个标签无人使用 → 结果为空集
            if (result == null) {
                result = ids;
            } else {
                result.retainAll(ids);
            }
        }
        return result;
    }

    @Override
    public Set<UUID> assetIdsWithTag(String tagName, @Nullable Set<UUID> visibleAssetIds) {
        if (!StringUtils.hasText(tagName)) {
            return Set.of();
        }
        Set<UUID> ids = new HashSet<>();
        for (AssetTag tag : assetTagRepository.findByTag(tagName.trim())) {
            UUID assetId = tag.getAssetId();
            if (assetId == null) {
                continue;
            }
            if (visibleAssetIds != null && !visibleAssetIds.contains(assetId)) {
                continue;
            }
            ids.add(assetId);
        }
        return ids;
    }
}
