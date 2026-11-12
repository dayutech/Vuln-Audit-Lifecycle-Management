package com.vulnaudit.service;

import com.vulnaudit.dto.FormDefinitionRequests;
import com.vulnaudit.dto.FormDefinitionVO;
import com.vulnaudit.dto.FormFieldVO;
import com.vulnaudit.dto.PageResponse;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 自定义填表系统 · 模板与字段 CRUD（t178，ADMIN）
 *
 * <p>规则（与 README §72 一致）：
 * <ul>
 *   <li>模板 `code` 唯一且**含软删行**（编码不复用）；创建后不可改（改 → 403）；</li>
 *   <li>模板软删；已被表单实例引用 → 409（带引用数）；</li>
 *   <li>字段 `field_key` 同模板唯一；`field_type`/`source_type` 白名单校验；`SELECT` 必须给 options（JSON 数组）；</li>
 *   <li>字段**已有提交数据**（`form_value` 存在）→ 禁止改 `field_type` / 删除（409），可改 label/required/sort_order；</li>
 *   <li>写操作各写 1 条审计（`UPDATE_FORM_DEFINITION` / `UPDATE_FORM_FIELD`）。</li>
 * </ul>
 */
public interface FormDefinitionService {

    PageResponse<FormDefinitionVO> list(@Nullable String keyword, @Nullable Boolean enabled, int page, int size);

    FormDefinitionVO detail(UUID id);

    FormDefinitionVO create(FormDefinitionRequests.CreateDefinition request, @Nullable UUID operatorId);

    FormDefinitionVO update(UUID id, FormDefinitionRequests.UpdateDefinition request, @Nullable UUID operatorId);

    void delete(UUID id, @Nullable UUID operatorId);

    FormFieldVO addField(UUID definitionId, FormDefinitionRequests.FieldUpsert request, @Nullable UUID operatorId);

    FormFieldVO updateField(UUID definitionId, UUID fieldId, FormDefinitionRequests.FieldUpsert request,
                            @Nullable UUID operatorId);

    void deleteField(UUID definitionId, UUID fieldId, @Nullable UUID operatorId);

    /** 按给定顺序重排 sort_order（0..n-1 连续） */
    FormDefinitionVO reorderFields(UUID definitionId, java.util.List<UUID> fieldIds, @Nullable UUID operatorId);
}
