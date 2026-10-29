package com.vulnaudit.service.notify;

/**
 * 通知发送渠道
 */
public enum NotificationChannel {

    /**
     * 站内信（数据库落库，始终可用）
     */
    IN_APP("in_app"),

    /**
     * 邮件（SMTP）
     */
    EMAIL("email"),

    /**
     * 短信（当前仅保留配置位，未接入网关）
     */
    SMS("sms"),

    /**
     * Webhook 推送
     */
    WEBHOOK("webhook");

    private final String code;

    NotificationChannel(String code) {
        this.code = code;
    }

    /**
     * 数据库与接口中使用的渠道编码
     */
    public String getCode() {
        return code;
    }
}
