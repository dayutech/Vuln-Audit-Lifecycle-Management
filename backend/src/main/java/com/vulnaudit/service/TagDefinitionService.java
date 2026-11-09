package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.TagDefinitionUpsertRequest;
import com.vulnaudit.dto.TagDefinitionVO;
import com.vulnaudit.dto.TagOptionVO;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 标签定义服务（t141）
 *
 * <p>标签既是「资产分组」的替代（分组 → 标签迁移由 V30 完成），也是自动标签（客户名/项目名）的目录。
 */
public interface TagDefinitionService {

    /** 内置标签（builtin=true） */
    List<String> BUILTIN_TAGS = List.of("核心系统", "对外服务", "测试环境");

    /** 启动幂等补齐内置标签（表空时插入；已存在则跳过） */
    boolean ensureBuiltin();

    /** 登录即可：下拉 + 资产数（按可见范围收敛） */
    List<TagOptionVO> options(@Nullable Set<UUID> visibleAssetIds);

    // ------------------------------------------------------------------ 管理员

    PageResponse<TagDefinitionVO> adminList(@Nullable String keyword, boolean includeDeleted, int page, int size);

    TagDefinitionVO create(TagDefinitionUpsertRequest request, @Nullable UUID operatorId);

    TagDefinitionVO update(UUID id, TagDefinitionUpsertRequest request, @Nullable UUID operatorId);

    void delete(UUID id, @Nullable UUID operatorId);

    // ------------------------------------------------------------------ 自动标签

    /**
     * 给资产补标签（幂等）：标签定义不存在则自动创建（builtin=false）
     *
     * @return 本次新增的标签数（已存在的跳过）
     */
    int applyTags(UUID assetId, List<String> tagNames, @Nullable UUID operatorId);

    /** 标签名 → 资产 id 集合（AND/OR 筛选用；null 表示未指定筛选） */
    @Nullable
    Set<UUID> resolveAssetIdsByTags(@Nullable java.util.Collection<String> tags);

    /** 标签名 → 打了该标签的可见资产 id 集合（OR 语义，供 AND 求交/直接使用） */
    Set<UUID> assetIdsWithTag(String tagName, @Nullable Set<UUID> visibleAssetIds);
}
