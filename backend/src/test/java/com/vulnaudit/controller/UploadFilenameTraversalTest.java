package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetFile;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AssetFileRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 上传文件名路径穿越测试（t188，CWE-22）。
 *
 * <p>缺陷现场：资产文件/交付物/漏洞附件三处落盘都用
 * {@code dir.resolve(UUID + "_" + file.getOriginalFilename())}，而 {@code getOriginalFilename()}
 * 完全由客户端控制。提交 {@code filename="a/../../../x.sh"} 时 {@code ..} 段在规范化后会
 * 逃出上传目录，等价于「任意路径写入」。
 *
 * <p>修复口径：落盘名前先过 {@code FileNameSanitizer}（只留最后一段、去 {@code ..}）。
 * 本用例用真实 multipart 上传验证：落库的 filePath 必须仍在资产上传目录内且不含 {@code ..}。
 */
@DisplayName("上传文件名路径穿越防护")
class UploadFilenameTraversalTest extends BaseTest {

    private static final String MALICIOUS_NAME = "a/../../../../../../tmp/owl-pwned.sh";

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetFileRepository assetFileRepository;

    @Autowired
    private UserRepository userRepository;

    private Asset asset;

    @BeforeEach
    void setUpAsset() {
        String suffix = String.valueOf(System.nanoTime());
        User owner = userRepository.save(Objects.requireNonNull(User.builder()
                .username("traversal_owner_" + suffix)
                .passwordHash("$2a$10$encrypted")
                .email("traversal_" + suffix + "@test.com")
                .fullName("穿越测试负责人")
                .status("active")
                .build()));
        asset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("路径穿越测试资产")
                .code("TRAV-" + suffix)
                .targetType("web")
                .targetSource("internal")
                .description("用于验证上传文件名清洗")
                .priority("P1")
                .status("active")
                .ownerId(owner.getId())
                .build()));
    }

    @Test
    @DisplayName("恶意文件名不会写出上传目录：落库路径不含 .. 且仍在该资产目录下")
    void maliciousFilenameCannotEscapeUploadDirectory() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", MALICIOUS_NAME, "text/plain", "pwned".getBytes());

        mockMvc.perform(multipart("/api/v1/assets/{assetId}/files", asset.getId())
                        .file(file)
                        .param("category", "document")
                        .header("Authorization", adminToken))
                .andExpect(status().isCreated());

        AssetFile saved = assetFileRepository
                .findByAssetIdAndAssetVersionIdAndStatusOrderByCreatedAtDesc(asset.getId(), null, "active")
                .stream().findFirst().orElse(null);
        assertNotNull(saved, "上传应落库一条资产文件记录");

        String filePath = saved.getFilePath();
        // Windows 下 Path.toString() 用反斜杠，断言前统一成 POSIX 分隔符
        String normalizedPath = filePath.replace('\\', '/');
        assertFalse(normalizedPath.contains(".."), "落盘路径不得包含 ..（实际: " + filePath + "）");
        assertTrue(normalizedPath.contains("uploads/assets/" + asset.getId()),
                "落盘路径必须仍在资产上传目录内（实际: " + filePath + "）");
        assertFalse(saved.getName().contains("/"), "落库展示名不得包含路径分隔符（实际: " + saved.getName() + "）");
        assertFalse(saved.getName().contains(".."), "落库展示名不得包含 ..（实际: " + saved.getName() + "）");

        // 再验证「规范化之后」确实没有逃出上传根目录
        Path uploadRoot = Path.of("./uploads/assets").toAbsolutePath().normalize();
        Path resolved = Path.of(filePath).toAbsolutePath().normalize();
        assertTrue(resolved.startsWith(uploadRoot), "规范化后必须仍在上传根目录内（实际: " + resolved + "）");

        // 清理本次上传产生的真实文件，避免污染工作目录
        Files.deleteIfExists(resolved);
    }
}