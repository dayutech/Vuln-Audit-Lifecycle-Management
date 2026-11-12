package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 生成实例的结果（t179）
 *
 * @param snapshotFilled 从业务库成功取到快照的字段数（前端提示「本次取到 N 个」）
 * @param snapshotNull   sourceType 非空但取值为空/未实现的字段数（提示「M 个为空」）
 * @param fieldCount     实例字段总数
 */
@Schema(description = "表单实例创建结果")
public record FormSubmissionCreatedVO(
        UUID id,
        UUID definitionId,
        String status,
        int fieldCount,
        int snapshotFilled,
        int snapshotNull,
        List<String> skippedSources) {
}
