package com.vulnaudit.controller;

import com.vulnaudit.entity.Document;
import com.vulnaudit.entity.DocumentVersion;
import com.vulnaudit.entity.User;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.DocumentRepository;
import com.vulnaudit.repository.DocumentVersionRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.security.JwtUtils;
import com.vulnaudit.service.impl.DocumentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 文档版本号并发唯一性测试（t32）
 *
 * <p>背景：document_versions 此前无唯一约束，并发保存时两条快照会拿到相同 version，
 * 历史排序 / 回滚 / 审计追溯失真。修复后：V17 唯一索引 + 行级锁串行化 + saveAndFlush +
 * 有界重试（3 次），冲突耗尽返回 409「文档版本冲突，请重试」。
 *
 * <p>本类不使用 {@code @Transactional}（并发场景需要独立事务），用例结束手动清理数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("文档版本并发唯一性测试")
class DocumentVersionConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User user;
    private String token;
    private UUID documentId;

    @BeforeEach
    void setUp() throws Exception {
        user = userRepository.save(Objects.requireNonNull(User.builder()
                .username("docconcurrency_" + System.nanoTime())
                .email("docconcurrency_" + System.nanoTime() + "@test.com")
                .passwordHash(passwordEncoder.encode("Test@123456"))
                .fullName("文档并发测试用户")
                .status("active")
                .build()));

        token = "Bearer " + jwtUtils.generateToken(user.getUsername(),
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));

        // 通过接口创建文档：会同时写入 v1 快照
        var result = mockMvc.perform(post("/api/v1/documents")
                        .header("Authorization", token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper().writeValueAsString(Map.of(
                                "title", "并发测试文档-" + UUID.randomUUID(),
                                "category", "漏洞报告",
                                "summary", "初始化",
                                "content", "初始正文")))))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus(), "文档应创建成功");
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        documentId = UUID.fromString(extractId(body));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.fasterxml.jackson.databind.ObjectMapper springObjectMapper;

    private com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return springObjectMapper;
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
        int end = body.indexOf('"', begin);
        return body.substring(begin, end);
    }

    @Test
    @DisplayName("8 线程并发更新同一文档 - 无 5xx，版本号唯一且连续")
    void concurrentUpdate_versionsUniqueAndContiguous() throws Exception {
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
                    int status = mockMvc.perform(put("/api/v1/documents/" + documentId)
                                    .header("Authorization", token)
                                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                    .content(Objects.requireNonNull(objectMapper().writeValueAsString(Map.of(
                                            "content", "并发正文内容-" + index + "-" + System.nanoTime())))))
                            .andReturn().getResponse().getStatus();
                    statuses.add(status);
                } catch (Throwable t) {
                    unexpected.set(t);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(120, TimeUnit.SECONDS), "并发更新超时未完成");
        executor.shutdownNow();

        assertEquals(null, unexpected.get(), "并发更新不应抛出未捕获异常: " + unexpected.get());

        long ok = statuses.stream().filter(status -> status == 200).count();
        long conflicts = statuses.stream().filter(status -> status == 409).count();
        long serverErrors = statuses.stream().filter(status -> status >= 500).count();

        System.out.printf("文档并发更新结果: 200=%d, 409=%d, 500=%d%n", ok, conflicts, serverErrors);

        assertEquals(THREADS, statuses.size(), "所有线程都应拿到响应: " + statuses);
        assertEquals(0, serverErrors, "并发更新不得返回 5xx，实际状态: " + statuses);
        assertEquals(THREADS, ok + conflicts, "响应只能是 200 或 409，实际状态: " + statuses);

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionDesc(documentId);
        long distinct = versions.stream().map(DocumentVersion::getVersion).distinct().count();
        assertEquals(versions.size(), distinct, "同一文档的 version 不允许重复: " + versions);

        // 创建时 v1 + 每次成功更新 +1 → 版本号必须连续 1..N
        List<Integer> sorted = versions.stream().map(DocumentVersion::getVersion).sorted().toList();
        for (int i = 0; i < sorted.size(); i++) {
            assertEquals(i + 1, sorted.get(i), "版本号应连续递增: " + sorted);
        }
        assertEquals(1 + (int) ok, sorted.size(), "快照数量应为 1(创建) + 成功更新数");

        Document document = documentRepository.findById(documentId).orElseThrow();
        assertEquals(sorted.get(sorted.size() - 1), document.getVersion(), "documents.version 应与最新快照一致");
    }

    @Test
    @DisplayName("重试耗尽 - 快照恒撞唯一键时返回 409「文档版本冲突，请重试」")
    void retryExhausted_returnsConflict() {
        DocumentRepository documentRepositoryMock = Mockito.mock(DocumentRepository.class);
        com.vulnaudit.repository.DocumentAccessRepository accessRepositoryMock =
                Mockito.mock(com.vulnaudit.repository.DocumentAccessRepository.class);
        DocumentVersionRepository versionRepositoryMock = Mockito.mock(DocumentVersionRepository.class);

        Document existing = Document.builder()
                .id(documentId)
                .title("mock-document")
                .content("旧正文")
                .version(1)
                .authorId(user.getId())
                .build();

        // 每次尝试返回全新副本：模拟新事务读到「已提交的 version=1 / 旧正文」
        when(documentRepositoryMock.findByIdForUpdate(documentId)).thenAnswer(invocation -> java.util.Optional.of(
                Document.builder()
                        .id(documentId)
                        .title("mock-document")
                        .content("旧正文")
                        .version(1)
                        .authorId(user.getId())
                        .build()));
        when(documentRepositoryMock.saveAndFlush(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepositoryMock.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "Unique index or primary key violation: uk_document_versions_document_version"));

        DocumentServiceImpl service = new DocumentServiceImpl(documentRepositoryMock, accessRepositoryMock,
                versionRepositoryMock, transactionManager);

        Document patch = Document.builder().content("新正文-" + UUID.randomUUID()).build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateDocumentWithSnapshot(documentId, patch, user.getId()));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals("文档版本冲突，请重试", exception.getMessage());
        verify(versionRepositoryMock, times(3)).saveAndFlush(any());
    }

    @Test
    @DisplayName("重试成功 - 首次冲突后在新事务中重算版本号并写入快照")
    void retryOnceThenSucceeds() {
        DocumentRepository documentRepositoryMock = Mockito.mock(DocumentRepository.class);
        com.vulnaudit.repository.DocumentAccessRepository accessRepositoryMock =
                Mockito.mock(com.vulnaudit.repository.DocumentAccessRepository.class);
        DocumentVersionRepository versionRepositoryMock = Mockito.mock(DocumentVersionRepository.class);

        Document existing = Document.builder()
                .id(documentId)
                .title("mock-document")
                .content("旧正文")
                .version(1)
                .authorId(user.getId())
                .build();

        // 每次尝试都返回「同一数据库状态的副本」，模拟新事务读到已提交的 version=1
        when(documentRepositoryMock.findByIdForUpdate(documentId)).thenAnswer(invocation -> java.util.Optional.of(
                Document.builder()
                        .id(documentId)
                        .title("mock-document")
                        .content("旧正文")
                        .version(1)
                        .authorId(user.getId())
                        .build()));
        when(documentRepositoryMock.saveAndFlush(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepositoryMock.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "Unique index or primary key violation: uk_document_versions_document_version"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentServiceImpl service = new DocumentServiceImpl(documentRepositoryMock, accessRepositoryMock,
                versionRepositoryMock, transactionManager);

        Document patch = Document.builder().content("新正文-" + UUID.randomUUID()).build();
        Document result = service.updateDocumentWithSnapshot(documentId, patch, user.getId());

        assertEquals(2, result.getVersion(), "重试后版本号应为 2");
        verify(versionRepositoryMock, times(2)).saveAndFlush(any());
    }
}
