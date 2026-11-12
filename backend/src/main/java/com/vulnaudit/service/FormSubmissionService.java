package com.vulnaudit.service;

import com.vulnaudit.dto.FormSubmissionCreatedVO;
import com.vulnaudit.dto.FormSubmissionRequests;
import com.vulnaudit.dto.FormSubmissionSummaryVO;
import com.vulnaudit.dto.FormSubmissionVO;
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
}
