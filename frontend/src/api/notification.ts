import request from '@/utils/request';
import type { AnnouncementListResponse } from '@/types/notification';
import type { PaginationParams } from '@/types/common';

export interface NotificationQueryParams extends PaginationParams {
  type?: string;
  is_read?: boolean;
}

export const getNotificationList = (params: NotificationQueryParams): Promise<any> => {
  return request.get('/notifications', { params });
};

export const markNotificationRead = (id: string): Promise<any> => {
  return request.put(`/notifications/${id}/read`);
};

export const markAllNotificationRead = (notification_ids: string[]): Promise<any> => {
  return request.put('/notifications/batch-read', { notification_ids });
};

export const getUnreadNotificationCount = (): Promise<any> => {
  return request.get('/notifications/unread-count');
};

export const getAnnouncementList = (params: PaginationParams): Promise<AnnouncementListResponse> => {
  return request.get('/announcements', { params });
};
