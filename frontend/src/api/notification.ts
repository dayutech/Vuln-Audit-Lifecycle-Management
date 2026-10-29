/**
 * 通知中心 API 客户端。
 *
 * 全部对接后端 NotificationController（/api/v1/notifications）。
 * 实测契约（2026-10-29，部署实例 8089）：
 * - 列表/公告分页参数是 page（0 基）+ size；
 * - 设置更新体是 snake_case 键；
 * - 公告创建必须带 publishedAt。
 */
import axios from 'axios';
import request from '@/utils/request';
import type {
  AnnouncementPage,
  AnnouncementSaveRequest,
  AnnouncementVO,
  NotificationPage,
  NotificationRecipientVO,
  NotificationSettingUpdateRequest,
  NotificationSettingVO,
} from '@/types/notification';

export interface NotificationQueryParams {
  /** 1 基页码，内部转 0 基 */
  page?: number;
  page_size?: number;
  type?: string;
  is_read?: boolean;
}

const unwrap = <T,>(res: any): T => (res && typeof res === 'object' && 'data' in res ? (res.data as T) : (res as T));

const emptyPage = (pageSize: number) => ({
  content: [] as never[],
  totalElements: 0,
  totalPages: 0,
  currentPage: 0,
  pageSize,
});

/* ==================== 站内信 ==================== */

export const getNotificationList = async (params: NotificationQueryParams = {}): Promise<NotificationPage> => {
  const pageSize = params.page_size ?? 10;
  const res: any = await request.get('/notifications', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: pageSize,
      isRead: params.is_read,
    },
  });
  const payload = unwrap<NotificationPage | null>(res);
  if (!payload) return emptyPage(pageSize) as NotificationPage;
  return {
    content: Array.isArray(payload.content) ? payload.content : [],
    totalElements: payload.totalElements ?? 0,
    totalPages: payload.totalPages ?? 0,
    currentPage: payload.currentPage ?? 0,
    pageSize: payload.pageSize ?? pageSize,
  };
};

/**
 * 未读通知 id 集合。
 * 后端列表不返回 isRead，只能反查 isRead=false 的结果集合来做高亮。
 */
export const getUnreadNotificationIds = async (size = 200): Promise<string[]> => {
  const page = await getNotificationList({ page: 1, page_size: size, is_read: false });
  return page.content.map((item) => String(item.id));
};

export const getUnreadNotificationCount = async (): Promise<number> => {
  const res: any = await request.get('/notifications/unread-count');
  const count = unwrap<number>(res);
  return typeof count === 'number' ? count : Number(count ?? 0);
};

export const markNotificationRead = (id: string): Promise<any> => request.post(`/notifications/${id}/read`);

export const markAllNotificationRead = (): Promise<any> => request.post('/notifications/read-all');

export const deleteNotification = (id: string): Promise<any> => request.delete(`/notifications/${id}`);

/** 通知投递记录（含渠道/发送状态/失败原因） */
export const getNotificationRecipients = async (notificationId: string): Promise<NotificationRecipientVO[]> => {
  const res: any = await request.get(`/notifications/${notificationId}/recipients`);
  const payload = unwrap<NotificationRecipientVO[]>(res);
  return Array.isArray(payload) ? payload : [];
};

/* ==================== 通知设置 ==================== */

export const getNotificationSettings = async (): Promise<NotificationSettingVO[]> => {
  const res: any = await request.get('/notifications/settings');
  const payload = unwrap<NotificationSettingVO[]>(res);
  return Array.isArray(payload) ? payload : [];
};

export const updateNotificationSetting = (
  type: string,
  payload: NotificationSettingUpdateRequest
): Promise<NotificationSettingVO> =>
  request.put(`/notifications/settings/${encodeURIComponent(type)}`, payload);

/* ==================== 系统公告 ==================== */

export const getAnnouncementList = async (params: { page?: number; page_size?: number } = {}): Promise<AnnouncementPage> => {
  const pageSize = params.page_size ?? 10;
  const res: any = await request.get('/notifications/announcements', {
    params: { page: Math.max((params.page ?? 1) - 1, 0), size: pageSize },
  });
  const payload = unwrap<AnnouncementPage | null>(res);
  if (!payload) return emptyPage(pageSize) as AnnouncementPage;
  return {
    content: Array.isArray(payload.content) ? payload.content : [],
    totalElements: payload.totalElements ?? 0,
    totalPages: payload.totalPages ?? 0,
    currentPage: payload.currentPage ?? 0,
    pageSize: payload.pageSize ?? pageSize,
  };
};

export const getActiveAnnouncements = async (): Promise<AnnouncementVO[]> => {
  const res: any = await request.get('/notifications/announcements/active');
  const payload = unwrap<AnnouncementVO[]>(res);
  return Array.isArray(payload) ? payload : [];
};

export const createAnnouncement = (payload: AnnouncementSaveRequest): Promise<AnnouncementVO> =>
  request.post('/notifications/announcements', payload);

export const updateAnnouncement = (id: string, payload: AnnouncementSaveRequest): Promise<AnnouncementVO> =>
  request.put(`/notifications/announcements/${id}`, payload);

export const deleteAnnouncement = (id: string): Promise<any> => request.delete(`/notifications/announcements/${id}`);

/* ==================== 资产订阅 ==================== */

export const getSubscribedAssetIds = async (): Promise<string[]> => {
  const res: any = await request.get('/notifications/subscriptions');
  const payload = unwrap<string[]>(res);
  return Array.isArray(payload) ? payload.map((item) => String(item)) : [];
};

export const isAssetSubscribed = async (assetId: string): Promise<boolean> => {
  const ids = await getSubscribedAssetIds();
  return ids.includes(assetId);
};

export const subscribeAsset = (assetId: string): Promise<any> => request.post(`/notifications/subscriptions/${assetId}`);

export const unsubscribeAsset = (assetId: string): Promise<any> =>
  request.delete(`/notifications/subscriptions/${assetId}`);

/* ==================== 服务器时钟 ==================== */

let serverClockOffsetMs: number | null = null;

/**
 * 探测服务器时钟与本机时钟的偏差（毫秒）。
 *
 * 后端公告的“生效”判定用的是服务端 LocalDateTime.now()，部署机 TZ 可能是 UTC；
 * 浏览器若直接用本地时间作为 publishedAt，公告会长期不生效（实测偏差 8h）。
 * 这里用 HTTP 响应头 Date（GMT，秒级精度）反推服务端当前时间。
 */
export const getServerClockOffsetMs = async (): Promise<number> => {
  if (serverClockOffsetMs !== null) return serverClockOffsetMs;
  try {
    const token = localStorage.getItem('access_token');
    const resp = await axios.get('/api/v1/notifications/unread-count', {
      timeout: 8000,
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    });
    const dateHeader = (resp.headers?.date ?? '') as string;
    const serverMs = dateHeader ? Date.parse(dateHeader) : Number.NaN;
    serverClockOffsetMs = Number.isNaN(serverMs) ? 0 : serverMs - Date.now();
  } catch {
    serverClockOffsetMs = 0;
  }
  return serverClockOffsetMs;
};

/** 以服务端时钟为准的当前时间 */
export const getServerNow = async (): Promise<Date> => new Date(Date.now() + (await getServerClockOffsetMs()));
