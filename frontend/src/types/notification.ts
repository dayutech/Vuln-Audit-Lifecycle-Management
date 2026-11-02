/**
 * 通知中心类型定义。
 *
 * 契约来源：后端 com.vulnaudit.controller.NotificationController（实测 2026-10-29，
 * 部署实例 http://192.168.188.200:8089/api/v1）。
 *
 * 实测要点（勿改回 snake_case）：
 * 1) GET /notifications 返回 PageResponse<Notification>，即 { content, totalElements,
 *    totalPages, currentPage, pageSize }，列表元素是通知主体本身，**不含 isRead**；
 *    Jackson 默认 camelCase，字段为 createdAt / relatedType / relatedId。
 * 2) 未读状态只能通过 GET /notifications?isRead=false 反查（前端用未读 id 集合推导高亮）。
 * 3) GET /notifications/{id}/recipients 返回 NotificationRecipient 实体（camelCase）。
 * 4) PUT /notifications/settings/{type} 的**请求体**是 Map<String,Boolean>，
 *    键为 snake_case：enabled / notify_email / notify_sms / notify_webhook；
 *    **响应**是 NotificationSetting 实体，字段为 camelCase。
 * 5) 公告创建必须带 publishedAt（后端列为 NOT NULL，缺失即 500）；生效判定为
 *    publishedAt <= now AND (expiresAt IS NULL OR expiresAt > now)。
 */

import type { MessageKey } from '@/i18n';

/** 后端 PageResponse 信封 */
export interface PageEnvelope<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

/** 通知主体（后端 Notification 实体） */
export interface NotificationVO {
  id: string;
  title: string;
  content: string;
  type: string;
  priority?: string;
  senderId?: string | null;
  relatedType?: string | null;
  relatedId?: string | null;
  createdAt?: string;
}

/** 列表项：通知主体 + 前端推导的未读标记 */
export interface NotificationListItem extends NotificationVO {
  isRead: boolean;
}

export type NotificationPage = PageEnvelope<NotificationVO>;

/** 发送状态：pending / sent / partial / failed（后端 NotificationRecipient.sendStatus） */
export type NotificationSendStatus = 'pending' | 'sent' | 'partial' | 'failed' | string;

/**
 * 通知接收/投递记录。
 * channel / sendStatus / sentAt / errorMessage / recipientType 由后端 V13 迁移补齐，
 * 老版本部署（未升级）不会返回这几个字段，故全部可选。
 */
export interface NotificationRecipientVO {
  id: string;
  notificationId: string;
  userId: string;
  recipientType?: string;
  channel?: string;
  sendStatus?: NotificationSendStatus;
  sentAt?: string | null;
  errorMessage?: string | null;
  isRead: boolean;
  readAt?: string | null;
  createdAt?: string;
}

/** 通知设置（后端 NotificationSetting 实体） */
export interface NotificationSettingVO {
  id?: string;
  userId?: string;
  type: string;
  enabled: boolean;
  notifyEmail: boolean;
  notifySms: boolean;
  notifyWebhook: boolean;
}

/** PUT /notifications/settings/{type} 请求体：后端按 snake_case 取键 */
export interface NotificationSettingUpdateRequest {
  enabled?: boolean;
  notify_email?: boolean;
  notify_sms?: boolean;
  notify_webhook?: boolean;
}

/** 系统公告（后端 SystemAnnouncement 实体） */
export interface AnnouncementVO {
  id: string;
  title: string;
  content: string;
  targetRoles?: string | null;
  priority: string;
  publishedAt: string;
  expiresAt?: string | null;
  createdAt?: string;
}

export type AnnouncementPage = PageEnvelope<AnnouncementVO>;

/** 公告创建/更新请求体（publishedAt 必填） */
export interface AnnouncementSaveRequest {
  title: string;
  content: string;
  priority?: string;
  publishedAt: string;
  expiresAt?: string | null;
  targetRoles?: string | null;
}

/** 通知类型选项（后端实际发出的 type 字符串） */
export interface NotificationTypeOption {
  value: string;
  label: string;
  description: string;
}

export const NOTIFICATION_TYPE_OPTIONS: NotificationTypeOption[] = [
  { value: 'task_assigned', label: '任务分配', description: '任务指派、状态变更提醒' },
  { value: 'vulnerability_discovered', label: '漏洞发现', description: '漏洞提交、流转与复测动态' },
  { value: 'access_reminder', label: '访问审批提醒', description: '资产访问申请待审批提醒' },
  { value: 'access_escalated', label: '访问审批升级', description: '审批超时后自动升级提醒' },
  { value: 'asset_subscription', label: '资产动态订阅', description: '订阅资产的漏洞/任务变化' },
  { value: 'system', label: '系统消息', description: '平台公告类站内信' },
];

/**
 * 通知类型 → i18n 词条（t59：枚举展示统一走 t()，不再硬编码中文）。
 * 未知类型回落到 notifications.type.unknown，页面可再附原始 type 值。
 */
export const NOTIFICATION_TYPE_LABEL_KEYS: Record<string, MessageKey> = {
  task_assigned: 'notifications.type.taskAssigned',
  vulnerability_discovered: 'notifications.type.vulnerabilityDiscovered',
  access_reminder: 'notifications.type.accessReminder',
  access_escalated: 'notifications.type.accessEscalated',
  asset_subscription: 'notifications.type.assetSubscription',
  system: 'notifications.type.system',
};

/** 取通知类型的词条 key（页面配合 t() 使用） */
export const notificationTypeLabelKey = (type?: string | null): MessageKey =>
  (type && NOTIFICATION_TYPE_LABEL_KEYS[type]) || 'notifications.type.unknown';

/** @deprecated 兼容旧调用方（中文硬编码）；新代码请用 notificationTypeLabelKey + t() */
export const notificationTypeLabel = (type?: string | null): string => {
  if (!type) return '未知类型';
  return NOTIFICATION_TYPE_OPTIONS.find((item) => item.value === type)?.label ?? type;
};

/** 公告优先级标签/颜色（t59：label 走 message key，页面用 t(labelKey) 渲染） */
export const ANNOUNCEMENT_PRIORITY_KEYS: Record<string, { labelKey: MessageKey; color: string }> = {
  normal: { labelKey: 'notifications.ann.form.priorityNormal', color: 'default' },
  important: { labelKey: 'notifications.ann.form.priorityImportant', color: 'orange' },
  urgent: { labelKey: 'notifications.ann.form.priorityUrgent', color: 'red' },
};

/** @deprecated 中文优先级标签；新代码请用 ANNOUNCEMENT_PRIORITY_KEYS + t() */
export const ANNOUNCEMENT_PRIORITY_META: Record<string, { label: string; color: string }> = {
  normal: { label: '普通', color: 'default' },
  important: { label: '重要', color: 'orange' },
  urgent: { label: '紧急', color: 'red' },
};
