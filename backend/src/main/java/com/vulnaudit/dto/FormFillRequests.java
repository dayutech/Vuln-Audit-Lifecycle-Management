package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 填写 / 覆盖 / 退回 / 设置确认人 的请求体（t180）
 *
 * <p><b>请求里没有 `sourceValue` 字段</b>：快照不可写。Jackson 忽略未知字段，
 * 所以外部带 `sourceValue` 会被**静默忽略**（测试里明确断言这一点），库里快照保持不变。
 */
public final class FormFillRequests {

    private FormFillRequests() {
    }

    /** 单个字段的值（填写用） */
    @Schema(description = "字段填写值")
    public record ValueItem(@NotNull UUID fieldId, @Size(max = 20000) String filledValue) {
    }

    /** 批量填写（草稿保存 / 提交前保存都走这个） */
    @Schema(description = "批量填写")
    public record FillValues(@NotEmpty List<ValueItem> values) {
    }

    /** 单个字段的覆盖值（null = 清除覆盖） */
    @Schema(description = "字段覆盖值")
    public record OverrideItem(@NotNull UUID fieldId, @Size(max = 20000) String overrideValue) {
    }

    /** 批量覆盖（只影响导出） */
    @Schema(description = "批量覆盖")
    public record OverrideValues(@NotEmpty List<OverrideItem> values) {
    }

    /** 退回（note 必填） */
    @Schema(description = "退回表单")
    public record ReturnRequest(@Size(max = 2000) String note) {
    }

    /** 设置确认人（第一期单人） */
    @Schema(description = "设置确认人")
    public record SetConfirmer(@NotNull UUID confirmerId) {
    }
}
