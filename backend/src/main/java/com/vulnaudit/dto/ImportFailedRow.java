package com.vulnaudit.dto;

/**
 * 批量导入失败行
 *
 * @param line   行号（1 基，表头为第 1 行）
 * @param reason 失败原因
 */
public record ImportFailedRow(int line, String reason) {
}
