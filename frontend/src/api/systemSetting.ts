import request from '@/utils/request';
import { normalizeLoginTheme } from '@/utils/loginTheme';
import { translate } from '@/i18n';
import type { LoginThemeId } from '@/components/login/types';

/**
 * 管理员：登录页全局缺省主题（t109 后端 / t110 前端）。
 *
 * 契约（`AdminSettingsController`）：
 * - `GET  /api/v1/admin/settings/login-theme` → `{defaultTheme, updatedBy, updatedAt}`
 * - `PUT  /api/v1/admin/settings/login-theme` body `{defaultTheme}` → 200（同上结构）
 *   - 白名单复用后端 `LoginTheme` 枚举，非法值 400 且消息带「支持: …」
 *   - 写审计 `UPDATE_SYSTEM_SETTING`（`PUT` 端点已加入 AuditTrailInterceptor 的 SKIP_ENDPOINTS，
 *     一次修改只留 1 条业务审计）
 *   - 仅 ADMIN 可读写（PROJECT_LEADER / TEAM_LEADER / 工程师均 403，匿名 401）
 *
 * 语义边界：这里改的是「新访客看到的缺省主题」，**不写任何 `users.login_theme`**；
 * 已经自己选过主题的用户（本机有标记 / 账号偏好）不受影响。
 */

export interface AdminLoginThemeSetting {
  defaultTheme: LoginThemeId;
  /** 最后修改人用户名；从未改过（自愈插入的默认行）时为 null */
  updatedBy: string | null;
  /** ISO 时间字符串；从未改过时为 null */
  updatedAt: string | null;
}

const unwrap = (res: unknown): any => (res as any)?.data ?? res;

export const extractAdminLoginThemeSetting = (res: unknown): AdminLoginThemeSetting => {
  const payload = unwrap(res);
  return {
    defaultTheme: normalizeLoginTheme(payload?.defaultTheme) ?? 'pencil',
    updatedBy: payload?.updatedBy ?? null,
    updatedAt: payload?.updatedAt ?? null,
  };
};

export const getAdminLoginThemeSetting = async (): Promise<AdminLoginThemeSetting> => {
  const res = await request.get('/admin/settings/login-theme', { silent: true });
  return extractAdminLoginThemeSetting(res);
};

export const updateAdminLoginThemeSetting = async (defaultTheme: LoginThemeId): Promise<AdminLoginThemeSetting> => {
  const res = await request.put('/admin/settings/login-theme', { defaultTheme }, { silent: true });
  return extractAdminLoginThemeSetting(res);
};

/** 保存失败的类别（与 i18n `admin.settings.loginTheme.err*` 一一对应） */
export type AdminSettingFailureKind = 'badRequest' | 'unauthorized' | 'forbidden' | 'server' | 'network' | 'generic';

export interface AdminSettingFailure {
  kind: AdminSettingFailureKind;
  status?: number;
  /** 后端 message（t60 起按 Accept-Language 本地化） */
  backendMessage: string;
  /** 已本地化的可读提示 */
  text: string;
}

/**
 * 把管理员设置的失败翻译成可读提示。
 *
 * 400 单独成一类：后端会返回「不支持的登录页主题: xxx（支持: …）」这种带支持列表的消息，
 * 必须原样透出（运维/管理员才能知道该选什么），不要笼统替换成"参数错误"。
 */
export const describeAdminSettingFailure = (error: unknown): AdminSettingFailure => {
  const response = (error as any)?.response;
  const status: number | undefined = response?.status;
  const backendMessage: string = response?.data?.message ?? response?.data?.error?.message ?? '';

  if (!response) {
    return { kind: 'network', backendMessage, text: translate('admin.settings.loginTheme.errNetwork') };
  }
  if (status === 400) {
    return {
      kind: 'badRequest',
      status,
      backendMessage,
      text: translate('admin.settings.loginTheme.err400', { message: backendMessage || '400' }),
    };
  }
  if (status === 401) {
    return { kind: 'unauthorized', status, backendMessage, text: translate('admin.settings.loginTheme.err401') };
  }
  if (status === 403) {
    return { kind: 'forbidden', status, backendMessage, text: translate('admin.settings.loginTheme.err403') };
  }
  if (typeof status === 'number' && status >= 500) {
    return { kind: 'server', status, backendMessage, text: translate('admin.settings.loginTheme.err5xx', { status }) };
  }
  return { kind: 'generic', status, backendMessage, text: translate('admin.settings.loginTheme.errGeneric', { status: status ?? '?' }) };
};
