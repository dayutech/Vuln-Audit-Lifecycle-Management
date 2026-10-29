package com.vulnaudit.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CSV 解析工具（RFC 4180 简化实现）
 *
 * <p>功能说明：
 * <ul>
 *     <li>支持逗号分隔、双引号包裹、双引号转义（""）、字段内换行；</li>
 *     <li>支持 CRLF / LF / CR 三种换行，自动去除 UTF-8 BOM；</li>
 *     <li>返回记录时携带原始行号（1 基），便于把导入失败定位到具体行。</li>
 * </ul>
 *
 * <p>不引入第三方 CSV 依赖，保证离线环境可用。
 */
public final class CsvSupport {

    /**
     * UTF-8 BOM，Excel 导出的 CSV 常带该前缀
     */
    private static final char BOM = '\uFEFF';

    private CsvSupport() {
    }

    /**
     * 解析 CSV 文本
     *
     * @param content CSV 文本（可为 null）
     * @return 记录列表，空行会被跳过；每条记录带起始行号
     */
    public static List<CsvRecord> parse(String content) {
        List<CsvRecord> records = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return records;
        }

        String text = content.charAt(0) == BOM ? content.substring(1) : content;
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean pendingCr = false;

        int line = 1;
        int recordStartLine = 1;
        int index = 0;
        int length = text.length();

        while (index < length) {
            char current = text.charAt(index);

            if (inQuotes) {
                if (current == '"') {
                    if (index + 1 < length && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index += 2;
                        continue;
                    }
                    inQuotes = false;
                    index++;
                    continue;
                }
                if (current == '\n') {
                    line++;
                    pendingCr = false;
                }
                field.append(current);
                index++;
                continue;
            }

            switch (current) {
                case '"' -> {
                    inQuotes = true;
                    index++;
                }
                case ',' -> {
                    fields.add(field.toString());
                    field.setLength(0);
                    index++;
                }
                case '\r' -> {
                    // 结束当前记录；若紧随 \n 则由 pendingCr 吞掉
                    fields.add(field.toString());
                    field.setLength(0);
                    addRecord(records, recordStartLine, fields);
                    fields = new ArrayList<>();
                    line++;
                    recordStartLine = line;
                    pendingCr = true;
                    index++;
                }
                case '\n' -> {
                    index++;
                    if (pendingCr) {
                        pendingCr = false;
                        continue;
                    }
                    fields.add(field.toString());
                    field.setLength(0);
                    addRecord(records, recordStartLine, fields);
                    fields = new ArrayList<>();
                    line++;
                    recordStartLine = line;
                }
                default -> {
                    field.append(current);
                    index++;
                }
            }
        }

        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            addRecord(records, recordStartLine, fields);
        }
        return records;
    }

    /**
     * 归一化表头/列名：去 BOM、去空白与下划线、转小写
     *
     * <p>如 {@code targetType}、{@code Target_Type}、{@code target type} 均归一为 {@code targettype}。
     */
    public static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(header.length());
        for (char c : header.toCharArray()) {
            if (c == BOM || Character.isWhitespace(c) || c == '_' || c == '-' || c == '\u00A0') {
                continue;
            }
            builder.append(c);
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * 判断记录是否为空行（所有字段均为空白）
     */
    public static boolean isBlankRecord(CsvRecord record) {
        if (record == null || record.fields().isEmpty()) {
            return true;
        }
        for (String value : record.fields()) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static void addRecord(List<CsvRecord> records, int line, List<String> fields) {
        CsvRecord record = new CsvRecord(line, List.copyOf(fields));
        if (!isBlankRecord(record)) {
            records.add(record);
        }
    }

    /**
     * 一条 CSV 记录
     *
     * @param line   记录起始行号（1 基，含表头）
     * @param fields 字段列表
     */
    public record CsvRecord(int line, List<String> fields) {

        /**
         * 按索引取字段，越界返回空串
         */
        public String get(int index) {
            if (index < 0 || index >= fields.size()) {
                return "";
            }
            String value = fields.get(index);
            return value == null ? "" : value.trim();
        }
    }
}
