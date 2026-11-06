package com.vulnaudit.dto;

import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 行业类型创建/更新请求（t130）
 *
 * <p>更新时：**内置项的 code/name 不可改**（403「内置行业不可修改」），
 * 但允许调整 {@code sortOrder} 与 {@code enabled}；自定义项可改 name/sortOrder/enabled/parentCode。
 * {@code code} 只在创建时生效（更新时传了也会被忽略）。
 *
 * <p>非空校验放在服务层按场景判断（创建要 code+name，更新允许只传部分字段），
 * 因此这里只有长度约束。
 */
@Schema(description = "行业类型创建/更新请求")
public record IndustryTypeUpsertRequest(
        @Schema(description = "行业编码（创建必填；全局唯一）") @Size(max = 16) String code,
        @Schema(description = "行业名称") @Size(max = 100) String name,
        @Schema(description = "上级门类编码，可空") @Size(max = 16) String parentCode,
        @Schema(description = "排序值，越小越靠前") Integer sortOrder,
        @Schema(description = "是否启用") Boolean enabled) {

    /** 创建场景：code / name 必填 */
    public void requireCreateFields() {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "行业编码不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "行业名称不能为空");
        }
    }
}
