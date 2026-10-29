package com.vulnaudit.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户批量导入结果
 *
 * <p>在通用导入结果之外，额外返回本次创建的用户与初始密码：
 * users 表没有 {@code must_change_password} 列，因此初始密码只在响应中出现一次，
 * 由管理员分发并要求首次登录后修改（或走「管理员重置密码」流程）。
 *
 * @param totalRows   文件中的数据行数（不含表头与空行）
 * @param successRows 成功导入行数
 * @param failedRows  失败行明细
 * @param createdUsers 成功创建的用户（含初始密码）
 */
public record ImportUsersResult(
        int totalRows,
        int successRows,
        List<ImportFailedRow> failedRows,
        List<ImportedUser> createdUsers
) {

    public static ImportUsersResult empty() {
        return new ImportUsersResult(0, 0, List.of(), List.of());
    }

    public static ImportUsersResult of(int totalRows, int successRows,
                                       List<ImportFailedRow> failedRows, List<ImportedUser> createdUsers) {
        List<ImportFailedRow> failures = failedRows == null ? List.of() : new ArrayList<>(failedRows);
        List<ImportedUser> created = createdUsers == null ? List.of() : new ArrayList<>(createdUsers);
        return new ImportUsersResult(totalRows, successRows, List.copyOf(failures), List.copyOf(created));
    }
}
