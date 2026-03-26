package com.vulnaudit.controller;

import com.vulnaudit.BaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 仪表盘控制器测试类
 */
@DisplayName("仪表盘控制器测试")
public class DashboardControllerTest extends BaseTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUpDashboard() {
        // Dashboard测试使用默认的setUp()创建的用户
    }

    @Nested
    @DisplayName("仪表盘概览测试")
    class DashboardOverviewTests {

        @Test
        @DisplayName("获取我的仪表盘 - 成功")
        void getMyDashboard_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/my")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("资产统计测试")
    class AssetStatsTests {

        @Test
        @DisplayName("获取资产统计 - 成功")
        void getAssetStats_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/assets/stats")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取资产类型分布 - 成功")
        void getAssetTypeDistribution_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/assets/stats/type")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取资产优先级分布 - 成功")
        void getAssetPriorityDistribution_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/assets/stats/priority")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("漏洞统计测试")
    class VulnerabilityStatsTests {

        @Test
        @DisplayName("获取漏洞统计 - 成功")
        void getVulnerabilityStats_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取漏洞严重程度分布 - 成功")
        void getVulnerabilitySeverityDistribution_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats/severity")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取漏洞状态分布 - 成功")
        void getVulnerabilityStatusDistribution_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats/workflow-status")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取漏洞趋势 - 成功")
        void getVulnerabilityTrend_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/trends/vulnerabilities")
                    .header("Authorization", engineerToken)
                    .param("period", "month"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("任务统计测试")
    class TaskStatsTests {

        @Test
        @DisplayName("获取任务统计 - 成功")
        void getTaskStats_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/workload/stats")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取任务状态分布 - 成功")
        void getTaskStatusDistribution_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/workload/stats/tasks")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取工作量统计 - 成功")
        void getWorkloadStats_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/workload/stats")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("趋势测试")
    class TrendTests {

        @Test
        @DisplayName("获取任务趋势 - 成功")
        void getTaskTrend_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/trends/workload")
                    .header("Authorization", engineerToken)
                    .param("period", "month"))
                    .andExpect(status().isOk());
        }
    }
}
