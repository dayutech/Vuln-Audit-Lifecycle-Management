package com.vulnaudit.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果
 *
 * <p>部分成功是允许的：成功行已入库，失败行给出具体行号与原因。
 *
 * @param totalRows   文件中的数据行数（不含表头与空行）
 * @param successRows 成功导入行数
 * @param failedRows  失败行明细
 */
public record ImportResult(int totalRows, int successRows, List<ImportFailedRow> failedRows) {

    /**
     * 空结果
     */
    public static ImportResult empty() {
        return new ImportResult(0, 0, List.of());
    }

    /**
     * 便捷构造：拷贝失败明细，避免外部修改
     */
    public static ImportResult of(int totalRows, int successRows, List<ImportFailedRow> failedRows) {
        List<ImportFailedRow> failures = failedRows == null ? List.of() : new ArrayList<>(failedRows);
        return new ImportResult(totalRows, successRows, List.copyOf(failures));
    }
}
