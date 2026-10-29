package com.vulnaudit.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV 解析工具单元测试
 */
@DisplayName("CSV 解析工具测试")
class CsvSupportTest {

    @Test
    @DisplayName("解析标准 CSV - 表头与数据行、行号从 1 开始")
    void parse_standard() {
        String csv = "name,targetType,priority\n资产A,Web应用,P2\n资产B,系统,P1\n";

        List<CsvSupport.CsvRecord> records = CsvSupport.parse(csv);

        assertEquals(3, records.size());
        assertEquals(1, records.get(0).line());
        assertEquals(List.of("name", "targetType", "priority"), records.get(0).fields());
        assertEquals(2, records.get(1).line());
        assertEquals("资产A", records.get(1).get(0));
        assertEquals(3, records.get(2).line());
        assertEquals("P1", records.get(2).get(2));
    }

    @Test
    @DisplayName("CRLF 换行 - 不产生多余空记录")
    void parse_crlf() {
        String csv = "a,b\r\n1,2\r\n3,4\r\n";

        List<CsvSupport.CsvRecord> records = CsvSupport.parse(csv);

        assertEquals(3, records.size());
        assertEquals(List.of("3", "4"), records.get(2).fields());
        assertEquals(3, records.get(2).line());
    }

    @Test
    @DisplayName("UTF-8 BOM - 首个表头不带 BOM")
    void parse_bom() {
        String csv = "\uFEFFname,priority\n资产A,P2\n";

        List<CsvSupport.CsvRecord> records = CsvSupport.parse(csv);

        assertEquals(2, records.size());
        assertEquals("name", records.get(0).get(0));
        assertEquals("name", CsvSupport.normalizeHeader(records.get(0).get(0)));
    }

    @Test
    @DisplayName("引号包裹与转义 - 支持字段内逗号、双引号、换行")
    void parse_quotedFields() {
        String csv = "name,description\n\"资产,A\",\"含\"\"引号\"\"与\n换行的描述\"\n";

        List<CsvSupport.CsvRecord> records = CsvSupport.parse(csv);

        assertEquals(2, records.size());
        assertEquals("资产,A", records.get(1).get(0));
        assertEquals("含\"引号\"与\n换行的描述", records.get(1).get(1));
    }

    @Test
    @DisplayName("空行被跳过 - 行号仍按物理行计算")
    void parse_skipBlankLines() {
        String csv = "name\n\n资产A\n   \n资产B\n";

        List<CsvSupport.CsvRecord> records = CsvSupport.parse(csv);

        assertEquals(3, records.size());
        assertEquals(3, records.get(1).line());
        assertEquals("资产A", records.get(1).get(0));
        assertEquals(5, records.get(2).line());
    }

    @Test
    @DisplayName("字段越界 - 返回空串而不是抛异常")
    void parse_missingFieldReturnsEmpty() {
        List<CsvSupport.CsvRecord> records = CsvSupport.parse("a,b\n1\n");

        assertEquals(2, records.size());
        assertEquals("", records.get(1).get(1));
    }

    @Test
    @DisplayName("表头归一化 - 忽略大小写、下划线、空格与 BOM")
    void normalizeHeader_variants() {
        assertEquals("targettype", CsvSupport.normalizeHeader("targetType"));
        assertEquals("targettype", CsvSupport.normalizeHeader("Target_Type"));
        assertEquals("targettype", CsvSupport.normalizeHeader(" target type "));
        assertEquals("teamcode", CsvSupport.normalizeHeader("\uFEFFTeam-Code"));
        assertEquals("", CsvSupport.normalizeHeader(null));
    }

    @Test
    @DisplayName("空内容 - 返回空列表")
    void parse_empty() {
        assertTrue(CsvSupport.parse(null).isEmpty());
        assertTrue(CsvSupport.parse("").isEmpty());
        assertTrue(CsvSupport.parse("\n\n").isEmpty());
    }
}
