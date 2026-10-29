package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulnaudit.entity.Document;
import com.vulnaudit.entity.DocumentVersion;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.DocumentRepository;
import com.vulnaudit.repository.DocumentVersionRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.security.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 文档回滚快照测试（t37）
 *
 * <p>背景：回滚此前只改 documents 内容、不写快照 → 历史里看不到「回滚到 vN」、审计断链且版本号跳号。
 * 修复后回滚复用 t32 的版本号分配语义（行级锁 + saveAndFlush + 有界重试）：
 * 新增一条 version = max + 1 的快照，内容取目标历史版本，changelog 标注 {@code rollback from vN}，
 * 历史版本本身保持不变。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("文档回滚快照测试")
class DocumentRollbackSnapshotTest {

    private static final int THREADS = 8;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private User user;
    private String token;
    private UUID documentId;

    private static final String V1_CONTENT = "第一版正文";

    @BeforeEach
    void setUp() throws Exception {
        user = userRepository.save(Objects.requireNonNull(User.builder()
                .username("rollback_" + System.nanoTime())
                .email("rollback_" + System.nanoTime() + "@test.com")
                .passwordHash(passwordEncoder.encode("Test@123456"))
                .fullName("回滚测试用户")
                .status("active")
                .build()));

        token = "Bearer " + jwtUtils.generateToken(user.getUsername(), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // 创建文档 → v1 快照
        var created = mockMvc.perform(post("/api/v1/documents")
                        .header("Authorization", token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(Map.of(
                                "title", "回滚测试文档",
                                "category", "漏洞报告",
                                "summary", "初版摘要",
                                "content", V1_CONTENT)))))
                .andReturn();
        assertEquals(201, created.getResponse().getStatus(), "文档应创建成功");
        documentId = UUID.fromString(extractId(created.getResponse().getContentAsString(StandardCharsets.UTF_8)));

        // 编辑一次 → v2 快照
        var updated = mockMvc.perform(put("/api/v1/documents/" + documentId)
                        .header("Authorization", token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(Map.of(
                                "content", "第二版正文")))))
                .andReturn();
        assertEquals(200, updated.getResponse().getStatus(), "首次编辑应成功");
    }

    @AfterEach
    void cleanUp() {
        if (documentId != null) {
            documentVersionRepository.findByDocumentIdOrderByVersionDesc(documentId)
                    .forEach(version -> documentVersionRepository.deleteById(version.getId()));
            documentRepository.deleteById(documentId);
        }
        if (user != null) {
            userRepository.deleteById(user.getId());
        }
    }

    private String extractId(String body) {
        String marker = "\"id\":\"";
        int start = body.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("响应中未找到 id: " + body);
        }
        int begin = start + marker.length();
        return body.substring(begin, body.indexOf('"', begin));
    }

    private int rollback(Integer targetVersion) throws Exception {
        return mockMvc.perform(post("/api/v1/documents/" + documentId + "/versions/" + targetVersion + "/rollback")
                        .header("Authorization", token))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("回滚写新快照 - version = max+1、快照数 +1、内容等于目标版本、历史不变")
    void rollback_writesNewSnapshot() throws Exception {
        List<DocumentVersion> before = documentVersionRepository.findByDocumentIdOrderByVersionDesc(documentId);
        int maxBefore = before.stream().mapToInt(DocumentVersion::getVersion).max().orElse(0);
        String v1ContentBefore = documentVersionRepository.findByDocumentIdAndVersion(documentId, 1)
                .orElseThrow().getContent();

        assertEquals(200, rollback(1));

        List<DocumentVersion> after = documentVersionRepository.findByDocumentIdOrderByVersionDesc(documentId);
        assertEquals(before.size() + 1, after.size(), "回滚应新增一条快照");

        DocumentVersion newest = after.get(0);
        assertEquals(maxBefore + 1, newest.getVersion(), "新快照版本号应为 max+1");
        assertEquals(V1_CONTENT, newest.getContent(), "新快照内容应等于被回滚到的目标版本");
        assertTrue(newest.getChangelog() != null && newest.getChangelog().contains("rollback from v1"),
                "快照应标注回滚来源: " + newest.getChangelog());
        assertEquals(user.getId(), newest.getCreatedBy(), "快照 created_by 应为操作者");

        // 历史不可变：原 v1 快照内容未变
        assertEquals(v1ContentBefore, documentVersionRepository.findByDocumentIdAndVersion(documentId, 1)
                .orElseThrow().getContent(), "历史版本不允许被修改");

        Document document = documentRepository.findById(documentId).orElseThrow();
        assertEquals(maxBefore + 1, document.getVersion(), "documents.version 应指向新快照");
        assertEquals(V1_CONTENT, document.getContent(), "文档当前内容应为回滚目标内容");

        // 版本号连续且唯一
        List<Integer> versions = after.stream().map(DocumentVersion::getVersion).sorted().toList();
        for (int i = 0; i < versions.size(); i++) {
            assertEquals(i + 1, versions.get(i), "版本号应连续: " + versions);
        }
    }

    @Test
    @DisplayName("连续回滚两次 - 版本号连续递增（max+1、max+2）")
    void consecutiveRollbacks_versionsContinuous() throws Exception {
        assertEquals(200, rollback(1));
        int firstNewVersion = documentRepository.findById(documentId).orElseThrow().getVersion();
        String firstContent = documentRepository.findById(documentId).orElseThrow().getContent();

        assertEquals(200, rollback(1));
        int secondNewVersion = documentRepository.findById(documentId).orElseThrow().getVersion();

        assertEquals(firstNewVersion + 1, secondNewVersion, "第二次回滚应再 +1，不得跳号");
        assertEquals(V1_CONTENT, firstContent);

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionDesc(documentId);
        assertEquals(4, versions.size(), "创建 v1 + 编辑 v2 + 两次回滚 v3/v4");
        List<Integer> sorted = versions.stream().map(DocumentVersion::getVersion).sorted().toList();
        assertEquals(List.of(1, 2, 3, 4), sorted, "版本号必须连续");
        assertEquals("rollback from v1", versions.get(0).getChangelog());
        assertEquals("rollback from v1", versions.get(1).getChangelog());
    }

    @Test
    @DisplayName("回滚到不存在的版本 - 404")
    void rollbackToMissingVersion_returns404() throws Exception {
        assertEquals(404, rollback(99));
    }

    @Test
    @DisplayName("并发回滚 + 编辑（8 线程）- 无 5xx、版本号唯一且连续")
    void concurrentRollbackAndEdit_noServerErrors() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREADS);
        List<Integer> statuses = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        for (int i = 0; i < THREADS; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    int status;
                    if (index % 2 == 0) {
                        status = mockMvc.perform(post("/api/v1/documents/" + documentId + "/versions/1/rollback")
                                        .header("Authorization", token))
                                .andReturn().getResponse().getStatus();
                    } else {
                        status = mockMvc.perform(put("/api/v1/documents/" + documentId)
                                        .header("Authorization", token)
                                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(Map.of(
                                                "content", "并发编辑-" + index + "-" + System.nanoTime())))))
                                .andReturn().getResponse().getStatus();
                    }
                    statuses.add(status);
                } catch (Throwable t) {
                    unexpected.set(t);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(120, TimeUnit.SECONDS), "并发回滚/编辑超时未完成");
        executor.shutdownNow();

        assertNull(unexpected.get(), "并发执行不应抛出未捕获异常: " + unexpected.get());

        long ok = statuses.stream().filter(status -> status == 200).count();
        long conflicts = statuses.stream().filter(status -> status == 409).count();
        long serverErrors = statuses.stream().filter(status -> status >= 500).count();
        System.out.printf("文档并发回滚/编辑结果: 200=%d, 409=%d, 500=%d%n", ok, conflicts, serverErrors);

        assertEquals(THREADS, statuses.size(), "所有线程都应拿到响应: " + statuses);
        assertEquals(0, serverErrors, "并发回滚/编辑不得返回 5xx，实际状态: " + statuses);
        assertEquals(THREADS, ok + conflicts, "响应只能是 200 或 409，实际状态: " + statuses);

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionDesc(documentId);
        long distinct = versions.stream().map(DocumentVersion::getVersion).distinct().count();
        assertEquals(versions.size(), distinct, "版本号不允许重复: " + versions);

        List<Integer> sorted = versions.stream().map(DocumentVersion::getVersion).sorted().toList();
        for (int i = 0; i < sorted.size(); i++) {
            assertEquals(i + 1, sorted.get(i), "版本号必须连续无跳号: " + sorted);
        }

        Document document = documentRepository.findById(documentId).orElseThrow();
        assertEquals(sorted.get(sorted.size() - 1), document.getVersion(), "documents.version 应与最新快照一致");
    }
}
