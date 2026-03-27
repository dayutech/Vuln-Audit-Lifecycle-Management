package com.vulnaudit.controller;

import com.vulnaudit.BaseTest;
import com.vulnaudit.entity.Notification;
import com.vulnaudit.entity.NotificationRecipient;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.NotificationRepository;
import com.vulnaudit.repository.NotificationRecipientRepository;
import com.vulnaudit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 通知管理模块测试类
 */
@DisplayName("通知管理模块测试")
public class NotificationControllerTest extends BaseTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationRecipientRepository recipientRepository;

    @Autowired
    private UserRepository userRepository;

    private Notification testNotification;
    private User testUser;

    @BeforeEach
    void setUpNotification() {
        // 使用唯一后缀避免数据冲突
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        
        testUser = User.builder()
                .username("notificationuser_" + uniqueSuffix)
                .passwordHash("$2a$10$encrypted")
                .email("notify_" + uniqueSuffix + "@test.com")
                .fullName("通知测试用户")
                .status("active")
                .build();
        testUser = userRepository.save(testUser);

        testNotification = Notification.builder()
                .type("system")
                .title("测试通知")
                .content("这是一条测试通知内容")
                .createdAt(LocalDateTime.now())
                .build();
        testNotification = notificationRepository.save(testNotification);

        NotificationRecipient recipient = NotificationRecipient.builder()
                .notificationId(testNotification.getId())
                .userId(testUser.getId())
                .isRead(false)
                .build();
        recipientRepository.save(recipient);
    }

    @Nested
    @DisplayName("通知列表查询测试")
    class GetNotificationListTests {

        @Test
        @DisplayName("获取通知列表 - 成功")
        void getNotificationList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/notifications")
                    .header("Authorization", engineerToken)
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取未读通知列表 - 成功")
        void getUnreadNotifications_Success() throws Exception {
            mockMvc.perform(get("/api/v1/notifications")
                    .header("Authorization", engineerToken)
                    .param("isRead", "false"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取未读通知数量 - 成功")
        void getUnreadCount_Success() throws Exception {
            mockMvc.perform(get("/api/v1/notifications/unread-count")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("通知标记已读测试")
    class MarkReadTests {

        @Test
        @DisplayName("标记通知已读 - 成功")
        void markAsRead_Success() throws Exception {
            mockMvc.perform(post("/api/v1/notifications/" + testNotification.getId() + "/read")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("标记所有通知已读 - 成功")
        void markAllAsRead_Success() throws Exception {
            mockMvc.perform(post("/api/v1/notifications/read-all")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("删除通知测试")
    class DeleteNotificationTests {

        @Test
        @DisplayName("删除通知 - 成功")
        void deleteNotification_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/notifications/" + testNotification.getId())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("通知设置测试")
    class NotificationSettingTests {

        @Test
        @DisplayName("获取通知设置 - 成功")
        void getSettings_Success() throws Exception {
            mockMvc.perform(get("/api/v1/notifications/settings")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("更新通知设置 - 成功")
        void updateSettings_Success() throws Exception {
            String settingsJson = "{\"enabled\":true}";
            mockMvc.perform(put("/api/v1/notifications/settings/system")
                    .header("Authorization", engineerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(settingsJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("系统公告测试")
    class SystemAnnouncementTests {

        @Test
        @DisplayName("获取系统公告列表 - 成功")
        void getAnnouncements_Success() throws Exception {
            mockMvc.perform(get("/api/v1/notifications/announcements")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }
}
