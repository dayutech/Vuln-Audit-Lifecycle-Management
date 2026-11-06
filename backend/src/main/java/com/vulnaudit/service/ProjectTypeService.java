package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.ProjectTypeAdminVO;
import com.vulnaudit.dto.ProjectTypeOption;
import com.vulnaudit.dto.ProjectTypeUpsertRequest;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 项目类型服务（t130）
 *
 * <p>内置 5 项（`PENTEST` 渗透测试 / `CODE_AUDIT` 代码审计 / `EMERGENCY` 应急响应 /
 * `COMPLIANCE` 合规测评 / `TRAINING` 安全培训）：code/name 不可改、不可删，
 * 只能调整 sortOrder / enabled；管理员可新增/修改/删除自定义项。
 * 删除为软删（`deleted_at`），避免 `projects.type_code` 出现悬空引用。
 */
public interface ProjectTypeService {

    /** 内置项目类型（code, name, sortOrder） */
    List<Object[]> BUILTIN_PROJECT_TYPES = List.of(
            new Object[]{"PENTEST", "渗透测试", 10},
            new Object[]{"CODE_AUDIT", "代码审计", 20},
            new Object[]{"EMERGENCY", "应急响应", 30},
            new Object[]{"COMPLIANCE", "合规测评", 40},
            new Object[]{"TRAINING", "安全培训", 50}
    );

    /** 启动幂等补齐内置项（表为空时插入） */
    boolean ensureBuiltin();

    /** 下拉用：仅启用且未软删，内置优先 */
    List<ProjectTypeOption> options();

    /** 建项目时校验 typeCode：不存在/停用 → 400 */
    ProjectTypeOption requireEnabled(@Nullable String code);

    /** 按 code 尽力匹配（历史 type 值回填用），未命中返回 null */
    @Nullable
    ProjectTypeOption matchByCode(@Nullable String code);

    // ------------------------------------------------------------------ 管理员

    PageResponse<ProjectTypeAdminVO> adminList(@Nullable String keyword, boolean includeDisabled, int page, int size);

    ProjectTypeAdminVO create(ProjectTypeUpsertRequest request, @Nullable UUID operatorId);

    ProjectTypeAdminVO update(UUID id, ProjectTypeUpsertRequest request, @Nullable UUID operatorId);

    void delete(UUID id, @Nullable UUID operatorId);
}
