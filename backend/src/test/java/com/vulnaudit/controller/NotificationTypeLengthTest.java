package com.vulnaudit.controller;

import com.vulnaudit.entity.Notification;
import com.vulnaudit.repository.NotificationRepository;
import com.vulnaudit.service.NotificationService;
import com.vulnaudit.service.notify.NotificationTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * notifications.type 列长/默认值修复测试
 *
 * <p>背景：列宽曾为 VARCHAR(20)，而端点默认值 {@code vulnerability_discovered}（实测 24 字符）
 * 会在落库时抛 DataIntegrityViolationException（500）。修复后：
 * <ul>
 *   <li>不传 type → 200，落库为 vulnerability_discovered（不截断默认值）；</li>
 *   <li>50 字符可写入；</li>
 *   <li>51 字符返回 400（服务层前置校验），不再 500。</li>
 * </ul>
 */
@DisplayName("通知类型列长与默认值测试")
class NotificationTypeLengthTest extends BaseTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    private String sendUrl(UUID vulnerabilityId, UUID recipientId) {
        return "/api/v1/notifications/vulnerability-discovered"
                + "?vulnerabilityId=" + vulnerabilityId
                + "&recipientIds=" + recipientId;
    }

    private List<Notification> notificationWithType(String type) {
        return notificationRepository.findAll().stream()
                .filter(notification -> type.equals(notification.getType()))
                .toList();
    }

    @Test
    @DisplayName("不传 type - 200 且落库默认值 vulnerability_discovered（24 字符不被截断）")
    void sendWithoutType_persistsDefaultType() throws Exception {
        mockMvc.perform(post(sendUrl(UUID.randomUUID(), engineerUserId))
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        List<Notification> saved = notificationWithType(NotificationTypes.VULNERABILITY_DISCOVERED);
        assertEquals(1, saved.size());
        assertEquals("vulnerability_discovered", saved.get(0).getType());
        assertEquals(NotificationTypes.VULNERABILITY_DISCOVERED.length(), saved.get(0).getType().length());
        // 实测该默认值为 24 字符（vulnerability 13 + "_" + discovered 10），旧列宽 20 必然拒绝
        assertTrue(saved.get(0).getType().length() > 20, "默认类型长度必须大于旧列宽 20，否则复现不出本次 500");
        assertEquals("漏洞通知", saved.get(0).getTitle());
        assertNotNull(saved.get(0).getId());
    }

    @Test
    @DisplayName("type 为 50 个字符 - 200 且可写入")
    void sendWith50Chars_persists() throws Exception {
        String type50 = "t".repeat(NotificationTypes.MAX_LENGTH);

        mockMvc.perform(post(sendUrl(UUID.randomUUID(), engineerUserId))
                        .header("Authorization", engineerToken)
                        .param("type", type50))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        List<Notification> saved = notificationWithType(type50);
        assertEquals(1, saved.size());
        assertEquals(50, saved.get(0).getType().length());
    }

    @Test
    @DisplayName("type 为 51 个字符 - 返回 400 而不是 500，且不落库")
    void sendWith51Chars_returns400() throws Exception {
        String type51 = "t".repeat(NotificationTypes.MAX_LENGTH + 1);

        mockMvc.perform(post(sendUrl(UUID.randomUUID(), engineerUserId))
                        .header("Authorization", engineerToken)
                        .param("type", type51))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("50")));

        assertTrue(notificationWithType(type51).isEmpty(), "超长类型不应写入数据库");
    }

    @Test
    @DisplayName("服务层：type 为空时兜底为 system，超长直接 400")
    void serviceLayer_normalizesAndValidates() {
        notificationService.sendNotification(engineerUserId, null, "空类型通知", "content");
        assertEquals(1, notificationWithType(NotificationTypes.SYSTEM).size());

        try {
            notificationService.sendNotification(engineerUserId, "x".repeat(NotificationTypes.MAX_LENGTH + 1), "超长", "content");
            throw new AssertionError("超长 type 应抛出 BusinessException");
        } catch (com.vulnaudit.exception.BusinessException e) {
            assertEquals(400, e.getErrorCode().getCode());
        }
    }

    @Test
    @DisplayName("常量类：所有已知类型长度均不超过列宽 50")
    void allKnownTypes_fitIntoColumn() {
        assertTrue(NotificationTypes.ALL.size() >= 8, "应覆盖设计文档中的通知类型");
        for (String type : NotificationTypes.ALL) {
            assertNotNull(type);
            assertTrue(type.length() <= NotificationTypes.MAX_LENGTH,
                    "类型 " + type + " 长度 " + type.length() + " 超过列宽 "
                            + NotificationTypes.MAX_LENGTH);
        }
    }
}
