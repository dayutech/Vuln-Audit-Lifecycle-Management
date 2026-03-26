package com.vulnaudit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulnaudit.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 测试基类
 * 提供通用测试配置和辅助方法
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class BaseTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtUtils jwtUtils;

    protected String adminToken;
    protected String leaderToken;
    protected String engineerToken;

    /**
     * 每个测试方法执行前的初始化
     */
    @BeforeEach
    protected void setUp() {
        // 生成测试用户Token
        adminToken = generateTestToken("admin", "ROLE_ADMIN");
        leaderToken = generateTestToken("leader1", "ROLE_LEADER");
        engineerToken = generateTestToken("engineer1", "ROLE_ENGINEER");
    }

    /**
     * 生成测试用JWT Token
     */
    protected String generateTestToken(String username, String... roles) {
        return "Bearer " + jwtUtils.generateToken(username,
            java.util.Arrays.stream(roles)
                .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority(r))
                .toList()
        );
    }

    /**
     * 执行GET请求（带认证）
     */
    protected ResultActions performGet(String url) throws Exception {
        return mockMvc.perform(get(url)
            .header("Authorization", engineerToken)
            .contentType("application/json"));
    }

    /**
     * 执行GET请求（管理员权限）
     */
    protected ResultActions performGetAsAdmin(String url) throws Exception {
        return mockMvc.perform(get(url)
            .header("Authorization", adminToken)
            .contentType("application/json"));
    }

    /**
     * 执行POST请求（带认证）
     */
    protected ResultActions performPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
            .header("Authorization", engineerToken)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * 执行POST请求（管理员权限）
     */
    protected ResultActions performPostAsAdmin(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
            .header("Authorization", adminToken)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * 执行PUT请求（带认证）
     */
    protected ResultActions performPut(String url, Object body) throws Exception {
        return mockMvc.perform(put(url)
            .header("Authorization", engineerToken)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * 执行DELETE请求（带认证）
     */
    protected ResultActions performDelete(String url) throws Exception {
        return mockMvc.perform(delete(url)
            .header("Authorization", engineerToken)
            .contentType("application/json"));
    }

    /**
     * 执行DELETE请求（管理员权限）
     */
    protected ResultActions performDeleteAsAdmin(String url) throws Exception {
        return mockMvc.perform(delete(url)
            .header("Authorization", adminToken)
            .contentType("application/json"));
    }
}
