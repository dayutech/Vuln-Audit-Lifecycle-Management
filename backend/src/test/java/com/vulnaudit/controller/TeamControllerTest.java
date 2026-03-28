package com.vulnaudit.controller;

import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 团队管理模块测试类
 */
@DisplayName("团队管理模块测试")
public class TeamControllerTest extends BaseTest {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    private Team testTeam;
    private User testLeader;

    @BeforeEach
    void setUpTeam() {
        // 使用唯一后缀避免数据冲突
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        
        testLeader = User.builder()
                .username("teamleader_" + uniqueSuffix)
                .passwordHash("$2a$10$encrypted")
                .email("leader_" + uniqueSuffix + "@team.com")
                .fullName("团队领导")
                .status("active")
                .build();
        testLeader = userRepository.save(testLeader);

        testTeam = Team.builder()
                .name("测试团队_" + uniqueSuffix)
                .description("用于测试的团队")
                .leaderId(testLeader.getId())
                .createdBy(testLeader.getId())
                .build();
        testTeam = teamRepository.save(testTeam);
    }

    @Nested
    @DisplayName("团队列表查询测试")
    class GetTeamListTests {

        @Test
        @DisplayName("获取团队列表 - 成功")
        void getTeamList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/teams")
                    .header("Authorization", adminToken)
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("获取团队列表 - 搜索")
        void getTeamList_Search() throws Exception {
            mockMvc.perform(get("/api/v1/teams")
                    .header("Authorization", adminToken)
                    .param("keyword", "测试"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("团队详情查询测试")
    class GetTeamTests {

        @Test
        @DisplayName("获取团队详情 - 成功")
        void getTeam_Success() throws Exception {
            mockMvc.perform(get("/api/v1/teams/" + testTeam.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").exists());
        }

        @Test
        @DisplayName("获取团队详情 - 团队不存在")
        void getTeam_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/teams/" + UUID.randomUUID())
                    .header("Authorization", adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("创建团队测试")
    class CreateTeamTests {

        @Test
        @DisplayName("创建团队 - 成功")
        void createTeam_Success() throws Exception {
            String teamJson = "{\"name\":\"新团队\",\"description\":\"新创建的团队\",\"leaderId\":\"" + testLeader.getId() + "\"}";

            mockMvc.perform(post("/api/v1/teams")
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(teamJson))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("更新团队测试")
    class UpdateTeamTests {

        @Test
        @DisplayName("更新团队 - 成功")
        void updateTeam_Success() throws Exception {
            String teamJson = "{\"name\":\"更新后的团队\",\"description\":\"更新后的描述\"}";

            mockMvc.perform(put("/api/v1/teams/" + testTeam.getId())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(teamJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("删除团队测试")
    class DeleteTeamTests {

        @Test
        @DisplayName("删除团队 - 成功")
        void deleteTeam_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/teams/" + testTeam.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("团队成员管理测试")
    class TeamMemberTests {

        @Test
        @DisplayName("获取团队成员列表 - 成功")
        void getTeamMembers_Success() throws Exception {
            mockMvc.perform(get("/api/v1/teams/" + testTeam.getId() + "/members")
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("添加团队成员 - 成功")
        void addMember_Success() throws Exception {
            User newUser = User.builder()
                    .username("newmember")
                    .passwordHash("$2a$10$encrypted")
                    .email("member@test.com")
                    .fullName("新成员")
                    .status("active")
                    .build();
            newUser = userRepository.save(newUser);

            mockMvc.perform(post("/api/v1/teams/" + testTeam.getId() + "/members")
                    .header("Authorization", adminToken)
                    .param("userId", newUser.getId().toString())
                    .param("role", "member"))
                    .andExpect(status().isOk());
        }
    }
}
