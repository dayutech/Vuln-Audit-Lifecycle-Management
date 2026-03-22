package com.vulnaudit.controller;

import com.vulnaudit.BaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 仪表盘模块测试类
 */
@DisplayName("仪表盘模块测试")
public class DashboardControllerTest extends BaseTest {

    @Nested
    @DisplayName("仪表盘概览测试")
    class DashboardOverviewTests {

        @Test
        @DisplayName("获取仪表盘概览 - 成功")
        void getOverview_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/overview")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("我的仪表盘测试")
    class MyDashboardTests {

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
            mockMvc.perform(get("/api/v1/dashboard/assets")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取资产类型分布 - 成功")
        void getAssetTypeDistribution_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/assets/type-distribution")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取资产优先级分布 - 成功")
        void getAssetPriorityDistribution_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/assets/priority-distribution")
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
            mockMvc.perform(get("/api/v1/dashboard/vulnerabilities")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取漏洞严重程度分布 - 成功")
        void getVulnerabilitySeverityDistribution_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/severity-distribution")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取漏洞状态分布 - 成功")
        void getVulnerabilityStatusDistribution_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/status-distribution")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取漏洞趋势 - 成功")
        void getVulnerabilityTrend_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/trend")
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
            mockMvc.perform(get("/api/v1/dashboard/tasks")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取任务状态分布 - 成功")
        void getTaskStatusDistribution_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/tasks/status-distribution")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取工作量统计 - 成功")
        void getWorkloadStats_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/workload")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("趋势统计测试")
    class TrendTests {

        @Test
        @DisplayName("获取资产趋势 - 成功")
        void getAssetTrend_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/trends/assets")
                    .header("Authorization", engineerToken)
                    .param("period", "month"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取任务趋势 - 成功")
        void getTaskTrend_Success() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/trends/tasks")
                    .header("Authorization", engineerToken)
                    .param("period", "month"))
                    .andExpect(status().isOk());
        }
    }
}
