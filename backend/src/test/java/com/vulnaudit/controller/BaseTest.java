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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class BaseTest {

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
        Role adminRole = roleRepository.save(Role.builder()
                .name("管理员").code("ROLE_ADMIN").description("管理员").isSystem(true).build());
        Role leaderRole = roleRepository.save(Role.builder()
                .name("组长").code("ROLE_LEADER").description("组长").isSystem(false).build());
        Role engineerRole = roleRepository.save(Role.builder()
                .name("工程师").code("ROLE_ENGINEER").description("工程师").isSystem(false).build());
        User adminUser = userRepository.save(User.builder()
                .username("admin_" + testCounter)
                .passwordHash(passwordEncoder.encode("admin123"))
                .email("admin_" + testCounter + "@test.com")
                .fullName("管理员")
                .status("active")
                .build());
        adminUserId = adminUser.getId();
        User leaderUser = userRepository.save(User.builder()
                .username("leader1_" + testCounter)
                .passwordHash(passwordEncoder.encode("leader123"))
                .email("leader1_" + testCounter + "@test.com")
                .fullName("组长")
                .status("active")
                .build());
        leaderUserId = leaderUser.getId();
        User engineerUser = userRepository.save(User.builder()
                .username("engineer1_" + testCounter)
                .passwordHash(passwordEncoder.encode("engineer123"))
                .email("engineer1_" + testCounter + "@test.com")
                .fullName("工程师")
                .status("active")
                .build());
        engineerUserId = engineerUser.getId();
        userRoleRepository.save(UserRole.builder()
                .userId(adminUserId)
                .roleId(adminRole.getId())
                .assignedBy(adminUserId)
                .assignedAt(LocalDateTime.now())
                .build());
        userRoleRepository.save(UserRole.builder()
                .userId(leaderUserId)
                .roleId(leaderRole.getId())
                .assignedBy(adminUserId)
                .assignedAt(LocalDateTime.now())
                .build());
        userRoleRepository.save(UserRole.builder()
                .userId(engineerUserId)
                .roleId(engineerRole.getId())
                .assignedBy(adminUserId)
                .assignedAt(LocalDateTime.now())
                .build());
        adminToken = generateTestToken("admin_" + testCounter, "ROLE_ADMIN");
        leaderToken = generateTestToken("leader1_" + testCounter, "ROLE_LEADER");
        engineerToken = generateTestToken("engineer1_" + testCounter, "ROLE_ENGINEER");
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
        return mockMvc.perform(get(url)
            .header("Authorization", engineerToken)
            .contentType("application/json"));
    }

    protected ResultActions performGetAsAdmin(String url) throws Exception {
        return mockMvc.perform(get(url)
            .header("Authorization", adminToken)
            .contentType("application/json"));
    }

    protected ResultActions performPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
            .header("Authorization", engineerToken)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions performPostAsAdmin(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
            .header("Authorization", adminToken)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions performPut(String url, Object body) throws Exception {
        return mockMvc.perform(put(url)
            .header("Authorization", engineerToken)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions performDelete(String url) throws Exception {
        return mockMvc.perform(delete(url)
            .header("Authorization", engineerToken)
            .contentType("application/json"));
    }

    protected ResultActions performDeleteAsAdmin(String url) throws Exception {
        return mockMvc.perform(delete(url)
            .header("Authorization", adminToken)
            .contentType("application/json"));
    }
}
