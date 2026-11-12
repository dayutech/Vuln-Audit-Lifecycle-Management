package com.vulnaudit.controller;

import com.vulnaudit.service.FormExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 填表系统导出（t181）：CSV / XLSX
 *
 * <p>CSV 带 **UTF-8 BOM**（Excel 打开中文不乱码）；两种格式的 `Content-Disposition` 都给 ASCII 回退
 * 与 **RFC 5987** `filename*=UTF-8''…`（与项目其它下载端点同口径）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "填表系统-导出", description = "CSV/XLSX 导出（effectiveValue 为单元格值）")
@SecurityRequirement(name = "bearerAuth")
public class FormExportController {

    private final FormExportService formExportService;

    @GetMapping("/api/v1/forms/submissions/export")
    @Operation(summary = "批量导出表单",
            description = "ADMIN/PROJECT_LEADER 可导出全部；其他用户只能导出与自己相关的（发起/被指派/确认）；"
                    + "单次上限默认 5000 行（超限 400）；format=csv|xlsx")
    public ResponseEntity<Resource> export(
            @Parameter(description = "模板ID（可空 = 全部）") @RequestParam(required = false) UUID definitionId,
            @Parameter(description = "状态：DRAFT/ASSIGNED/SUBMITTED/CONFIRMED/RETURNED/all")
            @RequestParam(required = false) String status,
            @Parameter(description = "创建时间起（含），ISO 日期") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "创建时间止（含），ISO 日期") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "csv 或 xlsx（默认 csv）") @RequestParam(defaultValue = "csv") String format,
            Authentication authentication) {
        return toResponse(formExportService.export(definitionId, status, from, to, format, authentication));
    }

    @GetMapping("/api/v1/forms/submissions/{id}/export")
    @Operation(summary = "导出单条表单", description = "权限同详情（发起人/被指派人/确认人/ADMIN；其他人 404）")
    public ResponseEntity<Resource> exportOne(@PathVariable UUID id,
                                              @RequestParam(defaultValue = "csv") String format,
                                              Authentication authentication) {
        return toResponse(formExportService.exportOne(id, format, authentication));
    }

    private ResponseEntity<Resource> toResponse(FormExportService.ExportFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(file.fileName()))
                .header(HttpHeaders.CONTENT_TYPE, file.contentType())
                .contentLength(file.content().length)
                .body(new ByteArrayResource(file.content()));
    }

    /** RFC 5987：ASCII 回退 + `filename*=UTF-8''…`（中文文件名不乱码） */
    static String contentDisposition(String fileName) {
        String fallback = fileName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "'");
        String encoded;
        try {
            encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            encoded = fallback;
        }
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }
}
