package com.vulnaudit.dto;

import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 项目类型创建/更新请求（t130）
 *
 * <p>与行业类型同一套规则：内置项（`PENTEST` / `CODE_AUDIT` / `EMERGENCY` / `COMPLIANCE` / `TRAINING`）
 * 的 code/name 不可改、不可删，只能调整 sortOrder / enabled。
 */
@Schema(description = "项目类型创建/更新请求")
public record ProjectTypeUpsertRequest(
        @Schema(description = "类型编码（创建必填；全局唯一，建议大写+下划线）") @Size(max = 32) String code,
        @Schema(description = "类型名称") @Size(max = 50) String name,
        @Schema(description = "排序值，越小越靠前") Integer sortOrder,
        @Schema(description = "是否启用") Boolean enabled) {

    /** 创建场景：code / name 必填 */
    public void requireCreateFields() {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目类型编码不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目类型名称不能为空");
        }
    }
}
