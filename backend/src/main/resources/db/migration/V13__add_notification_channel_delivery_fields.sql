-- 通知接收记录：补充外部通道与发送状态字段（邮件 / Webhook 通道落地）
ALTER TABLE notification_recipients
    ADD COLUMN IF NOT EXISTS recipient_type VARCHAR(20) DEFAULT 'specific_user',
    ADD COLUMN IF NOT EXISTS channel VARCHAR(50) DEFAULT 'in_app',
    ADD COLUMN IF NOT EXISTS send_status VARCHAR(20) DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS sent_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS error_message VARCHAR(500) NULL;

CREATE INDEX IF NOT EXISTS idx_notification_recipients_send_status
    ON notification_recipients (send_status);

CREATE INDEX IF NOT EXISTS idx_notification_recipients_notification_id
    ON notification_recipients (notification_id);
