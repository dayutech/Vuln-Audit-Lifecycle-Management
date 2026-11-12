package com.vulnaudit.service;

import com.vulnaudit.dto.FormSubmissionCreatedVO;
import com.vulnaudit.dto.FormSubmissionRequests;
import com.vulnaudit.dto.FormSubmissionSummaryVO;
import com.vulnaudit.dto.FormSubmissionVO;
import com.vulnaudit.dto.FormFillRequests;
import com.vulnaudit.dto.FormFillResultVO;
import com.vulnaudit.dto.FormValueVO;
import com.vulnaudit.dto.PageResponse;
import jakarta.annotation.Nullable;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * 表单实例：生成（库快照）+ 指派 + 实例级字段 + 查询（t179，第 2/5 块）
 *
 * <p>**核心语义**：`sourceValue` 只在生成时写入，之后任何路径都不改（模型上没有写回业务表的通道）；
 * `filledValue` 是被指派人填的；`overrideValue` 只影响导出。
 */
public interface FormSubmissionService {

    /**
     * 生成实例（发起人）
     *
     * @param definitionId 模板（必须启用）
     * @param subjectType  CUSTOMER | PROJECT | ASSET
     * @param subjectId    业务对象（必须存在、未软删、对调用者可见）
     * @return 含快照统计（snapshotFilled / snapshotNull / skippedSources）
     */
    FormSubmissionCreatedVO create(FormSubmissionRequests.Create request, Authentication authentication);

    /** 指派（仅发起人或 ADMIN；已提交/已确认 → 409） */
    FormSubmissionVO assign(UUID id, FormSubmissionRequests.Assign request, Authentication authentication);

    /** 发起人在本实例加手填字段（DRAFT/ASSIGNED 才可；库字段类型 → 400） */
    FormValueVO addInstanceField(UUID submissionId, FormSubmissionRequests.InstanceField request,
                                 Authentication authentication);

    /** 删除实例级字段（仅发起人；已填写 → 409） */
    void deleteInstanceField(UUID submissionId, UUID fieldId, Authentication authentication);

    /** 我发起的（分页，status 可空 = 全部；含进度 N/M） */
    PageResponse<FormSubmissionSummaryVO> findMine(Authentication authentication, @Nullable String status,
                                                   int page, int size);

    /** 详情（发起人 / 被指派人 / 确认人 / ADMIN；其他人 404） */
    FormSubmissionVO detail(UUID id, Authentication authentication);

    // ------------------------------------------------------------------ t180 填写 / 覆盖 / 确认退回

    /**
     * 保存填写值（**不校验必填**，草稿保存用；校验放在 submit）
     *
     * <p>权限：`assignedTo == 当前用户`（ASSIGNED）；或未指派/退回且自己是发起人。
     * 类型校验按 `fieldType`（NUMBER/DATE/SELECT/CHECKBOX 会校验）；**请求里的 sourceValue 被忽略**。
     */
    FormFillResultVO fillValues(UUID id, FormFillRequests.FillValues request, Authentication authentication);

    /** 提交：校验必填（`override ?? filled ?? source`）→ ASSIGNED/DRAFT/RETURNED → SUBMITTED */
    FormSubmissionVO submit(UUID id, Authentication authentication);

    /**
     * 修改覆盖（**只影响导出/展示，不改 filledValue，也不改业务库**）
     *
     * <p>权限：发起人 / ADMIN / 已设确认人；仅 `SUBMITTED`（或已 CONFIRMED）之后可设，否则 409；
     * `overrideValue = null` 表示清除覆盖（回落到 filledValue/sourceValue）。
     */
    FormFillResultVO setOverrides(UUID id, FormFillRequests.OverrideValues request, Authentication authentication);

    /** 确认：仅确认人或 ADMIN；SUBMITTED → CONFIRMED（写 confirmedAt） */
    FormSubmissionVO confirm(UUID id, Authentication authentication);

    /** 退回：仅确认人或 ADMIN；SUBMITTED → RETURNED；note 必填 */
    FormSubmissionVO returnSubmission(UUID id, FormFillRequests.ReturnRequest request, Authentication authentication);

    /** 设置确认人（第一期单人）：发起人或 ADMIN */
    FormSubmissionVO setConfirmer(UUID id, FormFillRequests.SetConfirmer request, Authentication authentication);

    /** 待我处理：`assignedTo == 当前用户`（含 RETURNED），分页，字段同 mine */
    PageResponse<FormSubmissionSummaryVO> findAssigned(Authentication authentication, @Nullable String status,
                                                       int page, int size);
}
