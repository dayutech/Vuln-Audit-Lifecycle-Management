package com.vulnaudit.controller;

import com.vulnaudit.entity.Tool;
import com.vulnaudit.entity.User;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.ToolRepository;
import com.vulnaudit.repository.ToolVersionRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.security.JwtUtils;
import com.vulnaudit.service.ToolService;
import com.vulnaudit.service.impl.ToolServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * 工具版本并发唯一键冲突测试（t28）
 *
 * <p>背景：`(tool_id, version_no)` 唯一约束的冲突发生在事务提交时，原实现把
 * {@code DataIntegrityViolationException} 的 catch 放在 save() 外层，异常逃逸后
 * 被全局处理器记成 500。修复后：saveAndFlush 让冲突在可控范围内抛出 →
 * 有界重试（3 次）→ 仍失败返回 409。
 *
 * <p>本测试类<b>不使用</b> {@code @Transactional}（并发场景必须有独立事务），
 * 因此每个用例结束手动清理数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("工具版本并发冲突测试")
class ToolVersionConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ToolService toolService;

    @Autowired
    private ToolRepository toolRepository;

    @Autowired
    private ToolVersionRepository toolVersionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User user;
    private Tool tool;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.save(Objects.requireNonNull(User.builder()
                .username("concurrency_" + System.nanoTime())
                .email("concurrency_" + System.nanoTime() + "@test.com")
                .passwordHash(passwordEncoder.encode("Test@123456"))
                .fullName("并发测试用户")
                .status("active")
                .build()));

        tool = toolRepository.save(Objects.requireNonNull(Tool.builder()
                .name("t28-并发工具-" + UUID.randomUUID())
                .description("并发版本上传测试")
                .category("扫描器")
                .createdBy(user.getId())
                .build()));

        token = "Bearer " + jwtUtils.generateToken(user.getUsername(),
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @AfterEach
    void cleanUp() {
        if (tool != null) {
            toolVersionRepository.findByToolIdOrderByCreatedAtDesc(tool.getId())
                    .forEach(version -> toolVersionRepository.deleteById(version.getId()));
            toolRepository.deleteById(tool.getId());
        }
        if (user != null) {
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    @DisplayName("8 线程并发上传同一工具版本 - 全部 201 或 409，不得出现 500，versionNo 唯一")
    void concurrentUpload_neverReturns500() throws Exception {
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
                    MockMultipartFile file = new MockMultipartFile("file", "pkg-" + index + ".bin",
                            MediaType.APPLICATION_OCTET_STREAM_VALUE, ("payload-" + index).getBytes(StandardCharsets.UTF_8));
                    int status = mockMvc.perform(multipart("/api/v1/tools/" + tool.getId() + "/versions")
                                    .file(file)
                                    .param("changelog", "并发上传 " + index)
                                    .header("Authorization", token))
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
        assertTrue(doneGate.await(120, TimeUnit.SECONDS), "并发上传超时未完成");
        executor.shutdownNow();

        assertEquals(null, unexpected.get(), "并发上传不应抛出未捕获异常: " + unexpected.get());

        long created = statuses.stream().filter(status -> status == 201).count();
        long conflicts = statuses.stream().filter(status -> status == 409).count();
        long serverErrors = statuses.stream().filter(status -> status >= 500).count();

        System.out.printf("并发上传结果: 201=%d, 409=%d, 500=%d%n", created, conflicts, serverErrors);

        assertEquals(THREADS, statuses.size(), "所有线程都应拿到响应: " + statuses);
        assertEquals(0, serverErrors, "并发上传不得返回 5xx，实际状态: " + statuses);
        assertEquals(THREADS, created + conflicts, "响应只能是 201 或 409，实际状态: " + statuses);
        assertTrue(created >= 1, "至少应有一个版本创建成功");

        List<com.vulnaudit.entity.ToolVersion> versions =
                toolVersionRepository.findByToolIdOrderByCreatedAtDesc(tool.getId());
        long distinctVersionNos = versions.stream().map(com.vulnaudit.entity.ToolVersion::getVersionNo).distinct().count();
        assertEquals(versions.size(), distinctVersionNos, "versionNo 不允许重复");
        assertTrue(versions.stream().allMatch(version -> version.getCreatedBy() != null), "createdBy 不允许为空");
    }

    @Test
    @DisplayName("重试耗尽 - 每次都撞唯一键时返回 409 而不是 500")
    void retryExhausted_returnsConflict() {
        ToolRepository toolRepositoryMock = Mockito.mock(ToolRepository.class);
        com.vulnaudit.repository.ToolDownloadRepository downloadRepositoryMock =
                Mockito.mock(com.vulnaudit.repository.ToolDownloadRepository.class);
        com.vulnaudit.repository.ToolTagRepository tagRepositoryMock =
                Mockito.mock(com.vulnaudit.repository.ToolTagRepository.class);
        ToolVersionRepository versionRepositoryMock = Mockito.mock(ToolVersionRepository.class);
        com.vulnaudit.security.CurrentUserProvider currentUserProviderMock =
                Mockito.mock(com.vulnaudit.security.CurrentUserProvider.class);

        Tool existingTool = Tool.builder().id(tool.getId()).name("mock-tool").createdBy(user.getId()).build();
        when(toolRepositoryMock.findById(tool.getId())).thenReturn(java.util.Optional.of(existingTool));
        when(toolRepositoryMock.findByIdForUpdate(tool.getId())).thenReturn(java.util.Optional.of(existingTool));
        when(currentUserProviderMock.requireCurrentUserId()).thenReturn(user.getId());
        when(versionRepositoryMock.findFirstByToolIdOrderByVersionNoDesc(tool.getId()))
                .thenReturn(java.util.Optional.empty());
        // 每次 saveAndFlush 都撞唯一键
        when(versionRepositoryMock.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        ToolServiceImpl service = new ToolServiceImpl(toolRepositoryMock, downloadRepositoryMock, tagRepositoryMock,
                versionRepositoryMock, currentUserProviderMock, transactionManager);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.uploadVersion(tool.getId(), null, "retry-test", null));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals("版本号冲突，请重试", exception.getMessage());
        verify(versionRepositoryMock, times(3)).saveAndFlush(any());
        assertNotNull(exception);
    }
}
