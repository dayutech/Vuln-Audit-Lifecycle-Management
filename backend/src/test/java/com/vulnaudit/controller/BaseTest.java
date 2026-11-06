package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.UserRoleRepository;
import com.vulnaudit.security.JwtUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
// t121 性能：原先 AFTER_EACH_TEST_METHOD 让 773 条用例各重建一次 Spring 上下文（约 12–20s/次，
// 全量 30+ 分钟的主因）。改为 AFTER_CLASS：类内共用一个上下文、类间仍然隔离（每个类跑完即关闭），
// 上下文数量从「用例数」降到「测试类数」。用例之间的状态由 setUp() 显式复位：
// clearDatabase() + 两个限流器（验证码/找回密码）的 resetThrottleState()。
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseTest {

    @Autowired
    private com.vulnaudit.service.VerificationCodeService verificationCodeServiceForReset;

    @Autowired
    private com.vulnaudit.service.PasswordResetService passwordResetServiceForReset;

    @Autowired
    private com.vulnaudit.service.LoginLockoutService loginLockoutServiceForReset;

    @Autowired
    private com.vulnaudit.service.AccountUnlockService accountUnlockServiceForReset;


    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtUtils jwtUtils;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected UserRoleRepository userRoleRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected EntityManager entityManager;

    protected String adminToken;
    protected String leaderToken;
    protected String engineerToken;

    protected UUID adminUserId;
    protected UUID leaderUserId;
    protected UUID engineerUserId;

    private static long testCounter = 0;

    @BeforeEach
    protected void setUp() {
        testCounter++;
        clearDatabase();
        // t121：类内共享上下文时，进程内的限流/冷却状态必须逐条用例复位，否则会串扰下一条用例
        verificationCodeServiceForReset.resetThrottleState();
        passwordResetServiceForReset.resetThrottleState();
        loginLockoutServiceForReset.reset();
        accountUnlockServiceForReset.resetThrottleState();
        Role adminRole = roleRepository.save(Objects.requireNonNull(Role.builder()
                .name("管理员").code("admin").description("管理员").isSystem(true).build()));
        Role leaderRole = roleRepository.save(Objects.requireNonNull(Role.builder()
                .name("组长").code("team_leader").description("组长").isSystem(false).build()));
        Role engineerRole = roleRepository.save(Objects.requireNonNull(Role.builder()
                .name("工程师").code("vulnerability_engineer").description("工程师").isSystem(false).build()));
        User adminUser = userRepository.save(Objects.requireNonNull(User.builder()
                .username("admin_" + testCounter)
                .passwordHash(passwordEncoder.encode("admin123"))
                .email("admin_" + testCounter + "@test.com")
                .fullName("管理员")
                .status("active")
                .build()));
        adminUserId = adminUser.getId();
        User leaderUser = userRepository.save(Objects.requireNonNull(User.builder()
                .username("leader1_" + testCounter)
                .passwordHash(passwordEncoder.encode("leader123"))
                .email("leader1_" + testCounter + "@test.com")
                .fullName("组长")
                .status("active")
                .build()));
        leaderUserId = leaderUser.getId();
        User engineerUser = userRepository.save(Objects.requireNonNull(User.builder()
                .username("engineer1_" + testCounter)
                .passwordHash(passwordEncoder.encode("engineer123"))
                .email("engineer1_" + testCounter + "@test.com")
                .fullName("工程师")
                .status("active")
                .build()));
        engineerUserId = engineerUser.getId();
        userRoleRepository.save(Objects.requireNonNull(UserRole.builder()
                .userId(adminUserId)
                .roleId(adminRole.getId())
                .assignedBy(adminUserId)
                .assignedAt(LocalDateTime.now())
                .build()));
        userRoleRepository.save(Objects.requireNonNull(UserRole.builder()
                .userId(leaderUserId)
                .roleId(leaderRole.getId())
                .assignedBy(adminUserId)
                .assignedAt(LocalDateTime.now())
                .build()));
        userRoleRepository.save(Objects.requireNonNull(UserRole.builder()
                .userId(engineerUserId)
                .roleId(engineerRole.getId())
                .assignedBy(adminUserId)
                .assignedAt(LocalDateTime.now())
                .build()));
        adminToken = Objects.requireNonNull(generateTestToken("admin_" + testCounter, "ROLE_ADMIN"));
        leaderToken = Objects.requireNonNull(generateTestToken("leader1_" + testCounter, "ROLE_TEAM_LEADER"));
        engineerToken = Objects.requireNonNull(generateTestToken("engineer1_" + testCounter, "ROLE_VULNERABILITY_ENGINEER"));
    }

    private void clearDatabase() {
        entityManager.createNativeQuery("DELETE FROM user_roles").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM roles").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM team_members").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM teams").executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    protected String generateTestToken(String username, String... roles) {
        return "Bearer " + jwtUtils.generateToken(username,
            java.util.Arrays.stream(roles)
                .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority(r))
                .toList()
        );
    }

    protected ResultActions performGet(String url) throws Exception {
        return mockMvc.perform(get(Objects.requireNonNull(url))
            .header("Authorization", engineerToken)
            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)));
    }

    protected ResultActions performGetAsAdmin(String url) throws Exception {
        return mockMvc.perform(get(Objects.requireNonNull(url))
            .header("Authorization", adminToken)
            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)));
    }

    protected ResultActions performPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(Objects.requireNonNull(url))
            .header("Authorization", engineerToken)
            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
            .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))));
    }

    protected ResultActions performPostAsAdmin(String url, Object body) throws Exception {
        return mockMvc.perform(post(Objects.requireNonNull(url))
            .header("Authorization", adminToken)
            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
            .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))));
    }

    protected ResultActions performPut(String url, Object body) throws Exception {
        return mockMvc.perform(put(Objects.requireNonNull(url))
            .header("Authorization", engineerToken)
            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
            .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))));
    }

    protected ResultActions performDelete(String url) throws Exception {
        return mockMvc.perform(delete(Objects.requireNonNull(url))
            .header("Authorization", engineerToken)
            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)));
    }

    protected ResultActions performDeleteAsAdmin(String url) throws Exception {
        return mockMvc.perform(delete(Objects.requireNonNull(url))
            .header("Authorization", adminToken)
            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)));
    }
}
