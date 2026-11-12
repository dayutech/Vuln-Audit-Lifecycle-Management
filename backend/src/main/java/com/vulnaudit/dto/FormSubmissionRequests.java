package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** 表单实例相关的请求体（t179） */
public final class FormSubmissionRequests {

    private FormSubmissionRequests() {
    }

    /**
     * 生成实例
     *
     * @param subjectType CUSTOMER | PROJECT | ASSET（决定库字段从哪个对象取值）
     * @param assignedTo  可选：直接指派给某人（有值 → ASSIGNED，否则 DRAFT）
     */
    @Schema(description = "生成表单实例")
    public record Create(
            @NotNull UUID definitionId,
            @NotBlank String subjectType,
            @NotNull UUID subjectId,
            @Size(max = 200) String title,
            UUID assignedTo) {
    }

    /** 指派（仅发起人或 ADMIN） */
    @Schema(description = "指派表单")
    public record Assign(
            @NotNull UUID assigneeId,
            @Size(max = 2000) String note) {
    }

    /** 发起人在本实例里加字段（只允许手填类型） */
    @Schema(description = "实例级字段")
    public record InstanceField(
            @NotBlank @Size(max = 200) String label,
            @NotBlank @Size(max = 20) String fieldType,
            Boolean required,
            @Size(max = 2000) String options) {
    }
}
