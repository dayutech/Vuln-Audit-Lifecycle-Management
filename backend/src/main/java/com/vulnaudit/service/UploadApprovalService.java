package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.UploadApprovalItem;
import com.vulnaudit.dto.UploadPendingResponse;
import jakarta.annotation.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 大文件上传审批（t160）
 *
 * <p>门限可配：`app.upload.approval-threshold-bytes`（默认 20MB）。超门限 → 只写临时区 + PENDING 记录；
 * 未超门限 → 直接落业务表（保持既有体验，返回 `status=DIRECT`）。
 *
 * <p>硬约束：PENDING/REJECTED 的文件**不写业务表**，因此不会出现在任何列表/下载/统计里；
 * 拒绝立即删临时文件，过期（`purge_after`）由 {@link #purgeExpired()} 清理。
 */
public interface UploadApprovalService {

    /** 提交上传（超门限走审批） */
    /**
     * 提交上传（超门限走审批）
     *
     * <p>t165：`displayName`/`description` 在**两条分支**（PENDING 与 DIRECT）都保留：
     * PENDING 存进审批表、批准时回填业务实体；DIRECT 直接用于建记录。
     *
     * @param displayName 用户填写的显示名（≤200，超长 400；为空则回落「文件名去扩展名」）
     * @param description 用户填写的描述（≤1000，超长 400）
     */
    UploadPendingResponse submit(MultipartFile file, String targetType, @Nullable String displayName,
                                 @Nullable String description, @Nullable UUID operatorId);

    /** 申请人查自己的请求 */
    UploadApprovalItem findOwn(UUID id, UUID operatorId);

    /** 管理端分页列表（status 可空 = 全部） */
    PageResponse<UploadApprovalItem> list(@Nullable String status, int page, int size);

    /**
     * t166：管理端分页列表（增加 keyword 与 requestedBy 过滤，便于按人查）
     */
    PageResponse<UploadApprovalItem> list(@Nullable String status, @Nullable String keyword,
                                          @Nullable UUID requestedBy, int page, int size);

    /**
     * t166：我的上传（只含 requested_by = 我），按 requestedAt 倒序
     *
     * @param status PENDING/APPROVED/REJECTED/EXPIRED/CANCELLED/all（白名单，非法 400；null=all）
     * @param keyword 匹配 fileName 或 displayName
     */
    PageResponse<UploadApprovalItem> findMine(UUID userId, @Nullable String status,
                                              @Nullable String keyword, int page, int size);

    /**
     * t166：撤回自己的 PENDING（删临时文件 + 标记 CANCELLED）
     *
     * <p>非本人 → 403；非 PENDING（已批准/已拒绝/已过期）→ 409。
     */
    UploadApprovalItem cancelMine(UUID id, UUID userId);

    /** 管理员批准：把临时文件移入正式存储 + 创建业务记录，返回目标实体 id */
    UploadApprovalItem approve(UUID id, @Nullable UUID reviewerId);

    /** 管理员拒绝：删除临时文件 + 标记 REJECTED */
    UploadApprovalItem reject(UUID id, @Nullable String note, @Nullable UUID reviewerId);

    /** 过期清理：PENDING 且 purge_after 已过 → EXPIRED + 删文件；返回清理条数 */
    int purgeExpired();

    /** 门限（字节），供文档/测试断言 */
    long approvalThresholdBytes();
}
