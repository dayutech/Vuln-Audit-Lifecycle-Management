package com.vulnaudit.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果
 *
 * <p>部分成功是允许的：成功行已入库，失败行给出具体行号与原因。
 *
 * <p>{@code notes}（t78）承载<b>成功行</b>上的提示，例如「未指定项目，已挂靠历史项目」——
 * 这类行是成功的（资产已入库），不能塞进 {@code failedRows}，但也**不能静默**：
 * 前端应在导入结果面板里单独展示。
 *
 * @param totalRows   文件中的数据行数（不含表头与空行）
 * @param successRows 成功导入行数
 * @param failedRows  失败行明细
 * @param notes       成功行的提示明细（可为空列表；当前仅资产导入「未指定项目」时产生）
 */
public record ImportResult(int totalRows, int successRows, List<ImportFailedRow> failedRows,
                           List<ImportNote> notes) {

    /**
     * 空结果
     */
    public static ImportResult empty() {
        return new ImportResult(0, 0, List.of(), List.of());
    }

    /**
     * 便捷构造（无提示）：拷贝失败明细，避免外部修改
     */
    public static ImportResult of(int totalRows, int successRows, List<ImportFailedRow> failedRows) {
        return of(totalRows, successRows, failedRows, List.of());
    }

    /**
     * 便捷构造（带成功行提示，t78）
     */
    public static ImportResult of(int totalRows, int successRows, List<ImportFailedRow> failedRows,
                                  List<ImportNote> notes) {
        List<ImportFailedRow> failures = failedRows == null ? List.of() : new ArrayList<>(failedRows);
        List<ImportNote> noteList = notes == null ? List.of() : new ArrayList<>(notes);
        return new ImportResult(totalRows, successRows, List.copyOf(failures), List.copyOf(noteList));
    }

    /**
     * 成功行提示（t78）
     *
     * @param line    行号（与 failedRows 同一编号体系，便于前端对齐展示）
     * @param message 提示文案
     */
    public record ImportNote(int line, String message) {
    }
}
