package com.vulnaudit.controller;

import com.vulnaudit.dto.ProjectRequest;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.User;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectPhaseRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.TaskRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目编码自动生成（t131）
 *
 * <p>规则：{@code PRJ-} + {@code yyyy-MM-dd} + {@code -} + 6 位随机大写字母，正则
 * {@code ^PRJ-\d{4}-\d{2}-\d{2}-[A-Z]{6}$}（按该正则与示例 {@code PRJ-2026-11-06-KXQZAB}
 * 计算总长为 <b>21</b>）；仅当请求未提供 code 时生成，显式传入则保留并校验格式与唯一性；
 * 更新未传 code 保留原编码；唯一索引冲突一律 409。
 */
@DisplayName("项目编码自动生成测试")
public class ProjectCodeTest extends BaseTest {

    private static final String PROJECTS = "/api/v1/projects";

    /** 规范格式样例（6 位大写字母） */
    private static final String VALID_CODE = "PRJ-2026-11-06-KXQZAB";

    private static final String CODE_REGEX = "^PRJ-\\d{4}-\\d{2}-\\d{2}-[A-Z]{6}$";

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Customer customer;

    @BeforeEach
    void setUpCustomer() {
        String suffix = String.valueOf(System.currentTimeMillis());
        customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T131-" + suffix)
                .name("编码用例客户_" + suffix)
                .status("active")
                .createdBy(adminUserId)
                .build()));
    }

    // ------------------------------------------------------------------ 工具

    private String projectBody(String code, String name) {
        StringBuilder sb = new StringBuilder("{");
        if (code != null) {
            sb.append("\"code\":\"").append(code).append("\",");
        }
        sb.append("\"customerId\":\"").append(customer.getId()).append("\",")
                .append("\"name\":\"").append(name).append("\",")
                .append("\"type\":\"PENTEST\",")
                .append("\"managerId\":\"").append(adminUserId).append("\"}");
        return sb.toString();
    }

    private Project createByApi(String code, String name) throws Exception {
        String response = mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(projectBody(code, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String createdId = objectMapper.readTree(response).path("data").path("id").asText();
        return projectRepository.findById(UUID.fromString(createdId)).orElseThrow();
    }

    // ------------------------------------------------------------------ 自动生成

    @Test
    @DisplayName("未传 code → 生成 PRJ-yyyy-MM-dd-6 位大写字母（总长 21），两次创建不同")
    void autoGenerateCode() throws Exception {
        Project first = createByApi(null, "自动编码项目A");
        Project second = createByApi(null, "自动编码项目B");

        assertTrue(first.getCode().matches(CODE_REGEX), "格式不符: " + first.getCode());
        assertEquals(21, first.getCode().length(), "总长应为 21（PRJ- + 10 + - + 6）");
        assertTrue(first.getCode().startsWith("PRJ-" + LocalDate.now() + "-"),
                "应以当天日期为日期段: " + first.getCode());
        assertNotEquals(first.getCode(), second.getCode(), "两次创建编码必须不同");
        assertTrue(projectRepository.existsByCode(first.getCode()));
    }

    @Test
    @DisplayName("生成编码避让既有行（含历史行），且不修改历史行")
    void generatedCodeAvoidsExistingRows() throws Exception {
        String today = LocalDate.now().toString();
        Project history = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-" + today + "-AAAAAA")
                .customerId(customer.getId())
                .name("占位历史项目")
                .type("PENTEST")
                .status("DRAFT")
                .managerId(adminUserId)
                .createdBy(adminUserId)
                .build()));

        Project created = createByApi(null, "新项目");

        assertNotEquals("PRJ-" + today + "-AAAAAA", created.getCode(), "不得复用已存在的编码");
        assertEquals("PRJ-" + today + "-AAAAAA",
                projectRepository.findById(history.getId()).orElseThrow().getCode(), "历史行编码不得变化");
        assertEquals(2, projectRepository.count(), "历史行 + 新行");
    }

    // ------------------------------------------------------------------ 显式编码

    @Test
    @DisplayName("显式传规范 code → 原样保留")
    void explicitCodeKept() throws Exception {
        Project project = createByApi(VALID_CODE, "显式编码项目");
        assertEquals(VALID_CODE, project.getCode());
    }

    @Test
    @DisplayName("显式传历史/导入编码（非日期形）→ 兼容保留，不报格式错误")
    void legacyCodeAccepted() throws Exception {
        assertEquals("PRJ-HISTORY", createByApi("PRJ-HISTORY", "历史编码项目").getCode());
        assertEquals("P-AGG-0001", createByApi("P-AGG-0001", "自定义编码项目").getCode());
    }

    @Test
    @DisplayName("显式传重复 code → 409（含与既有行冲突）")
    void duplicateExplicitCode409() throws Exception {
        createByApi(VALID_CODE, "首个项目");

        mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(projectBody(VALID_CODE, "重复项目")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString(VALID_CODE)));
    }

    @Test
    @DisplayName("非法格式：小写日期形 → 400")
    void invalidLowercaseCode400() throws Exception {
        mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody("prj-2026-11-06-kxqzab", "小写编码项目")))
                .andExpect(status().isBadRequest());

        assertEquals(0, projectRepository.count(), "非法编码不得落库");
    }

    @Test
    @DisplayName("非法格式：随机尾位数不对（5 位 / 7 位）→ 400")
    void invalidDigitCount400() throws Exception {
        mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody("PRJ-2026-11-06-KXQZ", "5位编码项目")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody("PRJ-2026-11-06-KXQZABC", "7位编码项目")))
                .andExpect(status().isBadRequest());

        assertEquals(0, projectRepository.count(), "非法编码不得落库");
    }

    // ------------------------------------------------------------------ 更新场景

    @Test
    @DisplayName("更新不传 code → 保留原编码；传新规范 code → 变更生效")
    void updateKeepsCode() throws Exception {
        Project project = createByApi(null, "待更新项目");
        String originalCode = project.getCode();

        mockMvc.perform(put(PROJECTS + "/" + project.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody(null, "待更新项目-改名")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(originalCode));

        assertEquals(originalCode,
                projectRepository.findById(project.getId()).orElseThrow().getCode(),
                "未传 code 时应保留原编码");

        mockMvc.perform(put(PROJECTS + "/" + project.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody("PRJ-2026-11-06-ZZZZZZ", "待更新项目-换编码")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("PRJ-2026-11-06-ZZZZZZ"));
    }

    @Test
    @DisplayName("更新成他人已使用的 code → 409（原编码不变）")
    void updateDuplicateCode409() throws Exception {
        Project first = createByApi("PRJ-2026-11-06-AAAAAA", "项目一");
        Project second = createByApi("PRJ-2026-11-06-BBBBBB", "项目二");

        mockMvc.perform(put(PROJECTS + "/" + second.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody(first.getCode(), "项目二-撞码")))
                .andExpect(status().isConflict());

        assertEquals("PRJ-2026-11-06-BBBBBB",
                projectRepository.findById(second.getId()).orElseThrow().getCode(),
                "冲突时原编码必须保持不变");
    }

    // ------------------------------------------------------------------ 生成与插入同事务（Mockito 单测，不连库）

    /** 构造一个只依赖 Mapper 的服务实例（数据库行为全部由 mock 给出） */
    private ProjectServiceImpl mockedService(ProjectRepository projects, CustomerRepository customers,
                                             UserRepository users,
                                             AssetProjectLinkRepository links) {
        return new ProjectServiceImpl(projects, mock(ProjectPhaseRepository.class), customers,
                links, users, mock(TaskRepository.class),
                mock(com.vulnaudit.service.ProjectTypeService.class),
                // t140 软删依赖：单测里不需要真操作者与一级缓存
                mock(com.vulnaudit.security.CurrentUserProvider.class),
                mock(jakarta.persistence.EntityManager.class));
    }

    private ProjectRequest requestWithoutCode() {
        // 12 参：t131 的 code 可选 + t130 的 typeCode / projectCode 别名（都没给 → 自动生成）
        return new ProjectRequest(null, customer.getId(), "mock 项目", "PENTEST", adminUserId,
                null, null, Boolean.FALSE, null, null, null, null);
    }

    private CustomerRepository mockCustomers() {
        CustomerRepository customers = mock(CustomerRepository.class);
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
        return customers;
    }

    private UserRepository mockUsers() {
        UserRepository users = mock(UserRepository.class);
        User manager = User.builder().username("mgr").fullName("经理").build();
        manager.setId(adminUserId);
        when(users.findById(adminUserId)).thenReturn(Optional.of(manager));
        return users;
    }

    @Test
    @DisplayName("生成码冲突后重试成功（第 3 次命中可用编码），仍为规范格式")
    void retryAfterConflictSucceeds() {
        ProjectRepository projects = mock(ProjectRepository.class);
        // t140：查重改为「含已删」（countByCodeIncludingDeleted），前两次冲突、第三次可用
        when(projects.countByCodeIncludingDeleted(anyString())).thenReturn(1L, 1L, 0L);
        when(projects.saveAndFlush(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectServiceImpl service = mockedService(projects, mockCustomers(), mockUsers(),
                mock(AssetProjectLinkRepository.class));

        var response = service.create(requestWithoutCode(), adminUserId);

        assertNotNull(response.code());
        assertTrue(response.code().matches(CODE_REGEX), "重试后编码仍须规范: " + response.code());
        verify(projects, times(3)).countByCodeIncludingDeleted(anyString());
    }

    @Test
    @DisplayName("连续 10 次生成均冲突 → 业务异常（409），不是 500")
    void retryExhaustedThrows409() {
        ProjectRepository projects = mock(ProjectRepository.class);
        when(projects.countByCodeIncludingDeleted(anyString())).thenReturn(1L);

        ProjectServiceImpl service = mockedService(projects, mockCustomers(), mockUsers(),
                mock(AssetProjectLinkRepository.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(requestWithoutCode(), adminUserId));
        assertEquals(ErrorCode.PROJECT_CODE_EXISTS, ex.getErrorCode());
        assertEquals(409, ex.getCode());
        verify(projects, times(10)).countByCodeIncludingDeleted(anyString());
    }

    @Test
    @DisplayName("唯一索引冲突（并发写）→ 映射为 409 业务异常，不是 500")
    void uniqueIndexViolationMappedTo409() {
        ProjectRepository projects = mock(ProjectRepository.class);
        when(projects.countByCodeIncludingDeleted(anyString())).thenReturn(0L);
        when(projects.saveAndFlush(any(Project.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement; Duplicate entry 'PRJ-2026-11-06-ABCDEF' for key 'uk_projects_code'"));

        ProjectServiceImpl service = mockedService(projects, mockCustomers(), mockUsers(),
                mock(AssetProjectLinkRepository.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(requestWithoutCode(), adminUserId));
        assertEquals(ErrorCode.PROJECT_CODE_EXISTS, ex.getErrorCode());
        assertEquals(409, ex.getCode());
        verify(projects).saveAndFlush(argThat(p -> p != null && p.getCode() != null
                && p.getCode().matches(CODE_REGEX)));
    }
}
