package com.vulnaudit.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileNameSanitizer} 单元测试（t188）。
 *
 * <p>覆盖三类真实攻击面：POSIX/Windows 路径穿越、响应头注入（控制字符）、隐藏文件/畸形名。
 */
@DisplayName("上传文件名清洗（路径穿越防护）")
class FileNameSanitizerTest {

    @Test
    @DisplayName("POSIX 穿越：../../../etc/passwd 只保留最后一段")
    void stripsPosixTraversal() {
        assertEquals("passwd", FileNameSanitizer.sanitize("../../../etc/passwd", "x"));
        assertEquals("passwd", FileNameSanitizer.sanitize("a/b/c/passwd", "x"));
    }

    @Test
    @DisplayName("Windows 穿越：..\\..\\windows\\evil.exe 只保留最后一段")
    void stripsWindowsTraversal() {
        assertEquals("evil.exe", FileNameSanitizer.sanitize("..\\..\\windows\\evil.exe", "x"));
        assertEquals("evil.sh", FileNameSanitizer.sanitize("C:\\tmp\\evil.sh", "x"));
    }

    @Test
    @DisplayName("形如 report..pdf 的残留双点被替换，清洗结果永远不含 '..'")
    void removesDoubleDots() {
        String sanitized = FileNameSanitizer.sanitize("report..pdf", "x");
        assertFalse(sanitized.contains(".."), "清洗后不应含 ..");
        assertEquals("report_pdf", sanitized);
    }

    @Test
    @DisplayName("控制字符（\\r\\n\\0）被剔除，避免响应头注入")
    void stripsControlCharacters() {
        assertEquals("a.txt", FileNameSanitizer.sanitize("a\r\n.txt", "x").replace("\r", "").replace("\n", ""));
        assertEquals("evil.txt", FileNameSanitizer.sanitize("evil\0.txt", "x"));
    }

    @Test
    @DisplayName("前导点被去掉，避免落成隐藏文件")
    void stripsLeadingDots() {
        assertEquals("htaccess", FileNameSanitizer.sanitize("...htaccess", "x"));
    }

    @Test
    @DisplayName("空/缺失/纯点名字回退到默认名")
    void fallsBackWhenBlank() {
        assertEquals("unnamed-file", FileNameSanitizer.sanitize(null, null));
        assertEquals("unnamed-file", FileNameSanitizer.sanitize("   ", null));
        assertEquals("default.bin", FileNameSanitizer.sanitize("..", "default.bin"));
    }

    @Test
    @DisplayName("正常中文名与扩展名保持原样")
    void keepsNormalNames() {
        assertEquals("漏洞报告-v1.2.pdf", FileNameSanitizer.sanitize("漏洞报告-v1.2.pdf", "x"));
        assertEquals("a.png", FileNameSanitizer.sanitize("./a.png", "x"));
    }

    @Test
    @DisplayName("超长名被截断到上限内并保留尾部扩展名")
    void truncatesLongNames() {
        String longName = "a".repeat(500) + ".zip";
        String sanitized = FileNameSanitizer.sanitize(longName, "x");
        assertTrue(sanitized.length() <= 180, "长度应被截断");
        assertTrue(sanitized.endsWith(".zip"), "应保留扩展名");
    }

    @Test
    @DisplayName("isSafe 对清洗前后的判定")
    void isSafeConsistency() {
        assertTrue(FileNameSanitizer.isSafe(FileNameSanitizer.sanitize("../../etc/passwd", "x")));
        assertFalse(FileNameSanitizer.isSafe("../evil"));
        assertFalse(FileNameSanitizer.isSafe(".hidden"));
        assertFalse(FileNameSanitizer.isSafe("dir/file"));
        assertTrue(FileNameSanitizer.isSafe("normal.pdf"));
    }
}