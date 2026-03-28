import type { PaginatedResponse } from './common';

export interface NotificationContentVO {
  id: string;
  type: string;
  title: string;
  content: string;
  related_type?: string;
  related_id?: string;
  created_at: string;
}

export interface NotificationVO {
  id: string;
  notification: NotificationContentVO;
  is_read: boolean;
  read_at?: string;
  channel: string;
  send_status: string;
}

export type NotificationListResponse = PaginatedResponse<NotificationVO>;

export interface AnnouncementVO {
  id: string;
  title: string;
  content: string;
  created_at: string;
}

export type AnnouncementListResponse = PaginatedResponse<AnnouncementVO>;
