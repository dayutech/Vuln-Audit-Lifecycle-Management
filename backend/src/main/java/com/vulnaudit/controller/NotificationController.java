package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.Notification;
import com.vulnaudit.entity.SystemAnnouncement;
import com.vulnaudit.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通知管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "通知管理", description = "通知列表、已读、设置等接口")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "获取通知列表", description = "分页获取用户的通知列表")
    public ResponseEntity<ApiResponse<PageResponse<Notification>>> listNotifications(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "是否已读") @RequestParam(required = false) Boolean isRead) {
        
        log.info("获取通知列表: page={}, size={}, isRead={}", page, size, isRead);
        
        // TODO: 从当前用户获取userId
        UUID userId = null;
        PageResponse<Notification> result = notificationService.findByRecipientId(userId, page, size, isRead);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "获取未读通知数量", description = "获取用户未读通知的数量")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        log.info("获取未读通知数量");
        
        UUID userId = null;
        long count = notificationService.countUnread(userId);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "标记通知已读", description = "标记指定通知为已读")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID id) {
        log.info("标记通知已读: id={}", id);
        
        UUID userId = null;
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/read-all")
    @Operation(summary = "标记所有通知已读", description = "标记所有通知为已读")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        log.info("标记所有通知已读");
        
        UUID userId = null;
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 删除通知
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除通知", description = "删除指定通知")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable UUID id) {
        log.info("删除通知: id={}", id);
        
        UUID userId = null;
        notificationService.deleteNotification(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 发送漏洞通知
     */
    @PostMapping("/vulnerability-discovered")
    @Operation(summary = "发送漏洞通知", description = "发送漏洞发现通知给相关用户")
    public ResponseEntity<ApiResponse<Void>> sendVulnerabilityNotification(
            @Parameter(description = "漏洞ID") @RequestParam UUID vulnerabilityId,
            @Parameter(description = "通知类型") @RequestParam(defaultValue = "vulnerability_discovered") String type,
            @Parameter(description = "接收者ID列表") @RequestParam(required = false) List<UUID> recipientIds) {
        
        log.info("发送漏洞通知: vulnerabilityId={}, type={}", vulnerabilityId, type);
        
        notificationService.sendVulnerabilityNotification(vulnerabilityId, type, recipientIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 获取通知发送记录
     */
    @GetMapping("/{notificationId}/recipients")
    @Operation(summary = "获取通知发送记录", description = "获取指定通知的发送记录")
    public ResponseEntity<ApiResponse<List<?>>> getNotificationRecipients(@PathVariable UUID notificationId) {
        log.info("获取通知发送记录: notificationId={}", notificationId);
        
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotificationRecipients(notificationId)));
    }

    // ==================== 通知设置 ====================

    @GetMapping("/settings")
    @Operation(summary = "获取通知设置", description = "获取用户的通知设置列表")
    public ResponseEntity<ApiResponse<List<?>>> getSettings() {
        log.info("获取通知设置");
        
        UUID userId = null;
        return ResponseEntity.ok(ApiResponse.success(notificationService.getSettings(userId)));
    }

    @PutMapping("/settings/{type}")
    @Operation(summary = "更新通知设置", description = "更新指定类型的通知设置")
    public ResponseEntity<ApiResponse<?>> updateSetting(
            @PathVariable String type,
            @RequestBody Map<String, Boolean> setting) {
        
        log.info("更新通知设置: type={}", type);
        
        UUID userId = null;
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 系统公告 ====================

    @GetMapping("/announcements")
    @Operation(summary = "获取系统公告列表", description = "分页获取系统公告列表")
    public ResponseEntity<ApiResponse<PageResponse<SystemAnnouncement>>> listAnnouncements(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        
        log.info("获取系统公告列表: page={}, size={}", page, size);
        
        PageResponse<SystemAnnouncement> result = notificationService.findAllAnnouncements(page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/announcements/active")
    @Operation(summary = "获取活跃公告", description = "获取当前有效的系统公告")
    public ResponseEntity<ApiResponse<List<SystemAnnouncement>>> getActiveAnnouncements() {
        log.info("获取活跃公告");
        
        List<SystemAnnouncement> announcements = notificationService.findActiveAnnouncements();
        return ResponseEntity.ok(ApiResponse.success(announcements));
    }

    @PostMapping("/announcements")
    @Operation(summary = "创建系统公告", description = "创建新的系统公告")
    public ResponseEntity<ApiResponse<SystemAnnouncement>> createAnnouncement(
            @RequestBody SystemAnnouncement announcement) {
        
        log.info("创建系统公告: title={}", announcement.getTitle());
        
        SystemAnnouncement created = notificationService.createAnnouncement(announcement);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created));
    }

    @PutMapping("/announcements/{id}")
    @Operation(summary = "更新系统公告", description = "更新系统公告")
    public ResponseEntity<ApiResponse<SystemAnnouncement>> updateAnnouncement(
            @PathVariable UUID id,
            @RequestBody SystemAnnouncement announcement) {
        
        log.info("更新系统公告: id={}", id);
        
        SystemAnnouncement updated = notificationService.updateAnnouncement(id, announcement);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/announcements/{id}")
    @Operation(summary = "删除系统公告", description = "删除系统公告")
    public ResponseEntity<ApiResponse<Void>> deleteAnnouncement(@PathVariable UUID id) {
        log.info("删除系统公告: id={}", id);
        
        notificationService.deleteAnnouncement(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 资产订阅 ====================

    @PostMapping("/subscriptions/{assetId}")
    @Operation(summary = "订阅资产", description = "订阅指定资产的通知")
    public ResponseEntity<ApiResponse<Void>> subscribeAsset(@PathVariable UUID assetId) {
        log.info("订阅资产: assetId={}", assetId);
        
        UUID userId = null;
        notificationService.subscribeAsset(userId, assetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/subscriptions/{assetId}")
    @Operation(summary = "取消订阅资产", description = "取消订阅指定资产的通知")
    public ResponseEntity<ApiResponse<Void>> unsubscribeAsset(@PathVariable UUID assetId) {
        log.info("取消订阅资产: assetId={}", assetId);
        
        UUID userId = null;
        notificationService.unsubscribeAsset(userId, assetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "获取订阅的资产", description = "获取用户订阅的所有资产ID")
    public ResponseEntity<ApiResponse<List<UUID>>> getSubscriptions() {
        log.info("获取订阅的资产");
        
        UUID userId = null;
        List<UUID> assetIds = notificationService.getSubscribedAssetIds(userId);
        return ResponseEntity.ok(ApiResponse.success(assetIds));
    }
}
