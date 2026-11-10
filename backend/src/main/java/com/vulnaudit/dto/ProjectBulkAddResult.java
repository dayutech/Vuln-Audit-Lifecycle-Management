package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * 项目批量添加资产结果（t154）
 *
 * <p>**不整体失败**：单条资产不存在/已软删/无权限 → 记入 {@code failed}，其余照常处理；
 * 已挂靠同项目同期次 → 计入 {@code skipped}（幂等，不报错）。
 *
 * @param added   新增挂靠数
 * @param skipped 已存在而跳过的数量
 * @param failed  失败明细（按入参顺序）
 */
@Schema(description = "项目批量添加资产结果")
public record ProjectBulkAddResult(
        int added,
        int skipped,
        List<FailedItem> failed) {

    /** 失败明细：assetId + 资产编号 + 原因 */
    @Schema(description = "批量操作失败明细")
    public record FailedItem(UUID assetId, String code, String reason) {
    }
}
