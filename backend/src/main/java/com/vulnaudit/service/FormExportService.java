package com.vulnaudit.service;

import jakarta.annotation.Nullable;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 填表系统导出（t181，第 4/5 块）：CSV / XLSX
 *
 * <p>**单元格值一律走 {@link FormValues#effectiveValue}**（override ?? filled ?? source）：
 * 导出即最终值，不加「是否覆盖」标记，也不回写业务库。
 */
public interface FormExportService {

    /**
     * 批量导出
     *
     * @param definitionId 可空 = 全部模板
     * @param status       可空 = 全部；白名单 DRAFT/ASSIGNED/SUBMITTED/CONFIRMED/RETURNED/all（非法 400）
     * @param from         可空；按 createdAt 闭区间（当天 00:00:00 起）
     * @param to           可空；按 createdAt 闭区间（当天 23:59:59.999 止）
     * @param format       csv | xlsx（非法 400）
     */
    ExportFile export(@Nullable UUID definitionId, @Nullable String status, @Nullable LocalDate from,
                      @Nullable LocalDate to, String format, Authentication authentication);

    /** 单条导出（权限同详情：发起人/被指派人/确认人/ADMIN；其他人 404） */
    ExportFile exportOne(UUID id, String format, Authentication authentication);

    /**
     * 导出文件
     *
     * @param content     CSV 已含 UTF-8 BOM；XLSX 为 OOXML 字节
     * @param fileName    中文文件名（控制器按 RFC 5987 编码）
     * @param contentType `text/csv; charset=UTF-8` 或 XLSX MIME
     * @param rowCount    数据行数（不含表头），用于审计
     */
    record ExportFile(byte[] content, String fileName, String contentType, int rowCount) {
    }
}
