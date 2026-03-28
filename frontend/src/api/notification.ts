import request from '@/utils/request';
import type { AnnouncementListResponse } from '@/types/notification';
import type { PaginationParams } from '@/types/common';

export interface NotificationQueryParams extends PaginationParams {
  type?: string;
  is_read?: boolean;
}

export const getNotificationList = (params: NotificationQueryParams): Promise<any> => {
  return request.get('/notifications', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size,
      isRead: params.is_read,
    },
  });
};

export const markNotificationRead = (id: string): Promise<any> => {
  return request.post(`/notifications/${id}/read`);
};

export const markAllNotificationRead = (notification_ids: string[]): Promise<any> => {
  if (notification_ids.length === 0) {
    return Promise.resolve({ success: true });
  }
  return request.post('/notifications/read-all');
};

export const getUnreadNotificationCount = (): Promise<any> => {
  return request.get('/notifications/unread-count');
};

export const getAnnouncementList = (params: PaginationParams): Promise<AnnouncementListResponse> => {
  return request.get('/notifications/announcements', { params: { page: params.page, size: params.page_size } });
};
