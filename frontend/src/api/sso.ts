import request from '@/utils/request';

/**
 * 第三方 SSO 前端接入层（t106，后端 t104）。
 *
 * 端点一览：
 * - `GET    /auth/sso/providers`                    公开，仅返回「已开启 **且** 已配置凭据」的提供商；
 * - `GET    /auth/sso/{code}/authorize-url`         公开（未开启 400 / 未配置 503）；
 * - `POST   /auth/sso/{code}/callback`              `200` 已绑定（签发 token）/ `202` 陌生身份待审批 / `403` 账号不可用 / `400` state 无效 / `503` 未配置；
 * - `GET    /users/me/sso-bindings`                 我的绑定列表；
 * - `POST   /users/me/sso-bindings/{code}/start`    发起绑定（返回带「绑定态」的授权地址；已绑定 → 409）；
 * - `DELETE /users/me/sso-bindings/{code}`          解绑（body `{password}`，密码错误 403）；
 * - `GET    /admin/sso/providers`                   管理员：全部提供商（含未开启 / 未配置）；
 * - `PATCH  /admin/sso/providers/{code}`            管理员开关（body `{enabled}`）。
 *
 * 约定：全部 `silent: true`，由调用方按状态码做分级提示（登录页 / 账号设置 / 系统设置各有文案）。
 * 只有「列表」类调用会静默吞错并回落空数组（登录页不允许因为一个小请求而报错或阻塞）。
 */

export interface SsoProvider {
  code: string;
  name: string;
  icon?: string;
}

export interface AdminSsoProvider extends SsoProvider {
  enabled: boolean;
  configured: boolean;
  usable: boolean;
  scopes?: string;
}

export interface SsoBinding {
  id: string;
  code: string;
  externalId: string;
  displayName: string;
  boundAt: string;
}

export interface SsoAuthorize {
  url: string;
  state: string;
}

export interface SsoLoginPayload {
  token: string;
  userId?: number;
  username?: string;
  fullName?: string;
  email?: string;
  [key: string]: unknown;
}

export interface SsoCallbackResult {
  /** LOGIN（已签发 token）/ BOUND（绑定成功）/ PENDING_APPROVAL（已提交审批） */
  status: string;
  login?: SsoLoginPayload | null;
  provider?: string;
}

const unwrap = (res: unknown): any => (res as any)?.data ?? res;

/** 登录页回调地址：固定路径（各 provider 只需注册这一个 URI） */
export const ssoRedirectUri = (): string =>
  typeof window === 'undefined' ? '/auth/sso/callback' : `${window.location.origin}/auth/sso/callback`;

/** 登录页可见的提供商；失败当「没有 SSO」处理（不弹错、不阻塞登录） */
export const getSsoProviders = async (): Promise<SsoProvider[]> => {
  try {
    const res = await request.get('/auth/sso/providers', { silent: true, timeout: 5000 });
    const list = unwrap(res);
    if (!Array.isArray(list)) return [];
    return list
      .filter((item: any) => item && typeof item.code === 'string' && item.code.trim() !== '')
      .map((item: any) => ({
        code: String(item.code),
        name: String(item.name ?? item.code),
        icon: item.icon ? String(item.icon) : undefined,
      }));
  } catch {
    return [];
  }
};

export const getSsoAuthorizeUrl = async (code: string): Promise<SsoAuthorize> => {
  const res = await request.get(`/auth/sso/${encodeURIComponent(code)}/authorize-url`, {
    params: { redirectUri: ssoRedirectUri() },
    silent: true,
    timeout: 8000,
  });
  const payload = unwrap(res) ?? {};
  return { url: String(payload.url ?? ''), state: String(payload.state ?? '') };
};

export const postSsoCallback = async (provider: string, code: string, state: string): Promise<SsoCallbackResult> => {
  const res = await request.post(
    `/auth/sso/${encodeURIComponent(provider)}/callback`,
    { code, state },
    { silent: true, timeout: 15000 },
  );
  const payload = unwrap(res) ?? {};
  return {
    status: String(payload.status ?? ''),
    login: payload.login ?? null,
    provider: payload.provider ?? provider,
  };
};

export const getMySsoBindings = async (): Promise<SsoBinding[]> => {
  const res = await request.get('/users/me/sso-bindings', { silent: true });
  const list = unwrap(res);
  return Array.isArray(list) ? (list as SsoBinding[]) : [];
};

export const startSsoBind = async (code: string): Promise<SsoAuthorize> => {
  const res = await request.post(
    `/users/me/sso-bindings/${encodeURIComponent(code)}/start`,
    null,
    { params: { redirectUri: ssoRedirectUri() }, silent: true, timeout: 8000 },
  );
  const payload = unwrap(res) ?? {};
  return { url: String(payload.url ?? ''), state: String(payload.state ?? '') };
};

export const unbindSso = async (code: string, password: string): Promise<void> => {
  await request.delete(`/users/me/sso-bindings/${encodeURIComponent(code)}`, {
    data: { password },
    silent: true,
  });
};

export const adminListSsoProviders = async (): Promise<AdminSsoProvider[]> => {
  const res = await request.get('/admin/sso/providers', { silent: true });
  const list = unwrap(res);
  return Array.isArray(list)
    ? (list as any[]).map((item) => ({
        code: String(item.code),
        name: String(item.name ?? item.code),
        icon: item.icon ? String(item.icon) : undefined,
        enabled: Boolean(item.enabled),
        configured: Boolean(item.configured),
        usable: Boolean(item.usable),
        scopes: item.scopes ? String(item.scopes) : undefined,
      }))
    : [];
};

export const adminToggleSsoProvider = async (code: string, enabled: boolean): Promise<AdminSsoProvider> => {
  const res = await request.patch(
    `/admin/sso/providers/${encodeURIComponent(code)}`,
    { enabled },
    { silent: true },
  );
  const payload = unwrap(res) ?? {};
  return {
    code: String(payload.code ?? code),
    name: String(payload.name ?? code),
    icon: payload.icon ? String(payload.icon) : undefined,
    enabled: Boolean(payload.enabled),
    configured: Boolean(payload.configured),
    usable: Boolean(payload.usable),
    scopes: payload.scopes ? String(payload.scopes) : undefined,
  };
};

/** 记住「这次跳转是登录还是绑定」以及 provider（回调页据此调对应接口与文案） */
export const SSO_PENDING_KEY = 'sso.pending';

export interface SsoPendingState {
  provider: string;
  mode: 'login' | 'bind';
  startedAt: number;
}

export const rememberSsoPending = (state: SsoPendingState): void => {
  try {
    window.sessionStorage.setItem(SSO_PENDING_KEY, JSON.stringify(state));
  } catch {
    /* 隐私模式下忽略 */
  }
};

export const readSsoPending = (): SsoPendingState | null => {
  try {
    const raw = window.sessionStorage.getItem(SSO_PENDING_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as SsoPendingState;
    if (!parsed || typeof parsed.provider !== 'string') return null;
    return { provider: parsed.provider, mode: parsed.mode === 'bind' ? 'bind' : 'login', startedAt: Number(parsed.startedAt) || 0 };
  } catch {
    return null;
  }
};

export const clearSsoPending = (): void => {
  try {
    window.sessionStorage.removeItem(SSO_PENDING_KEY);
  } catch {
    /* 忽略 */
  }
};

/**
 * 记下本次流程并整页跳转到第三方授权页。
 *
 * 收在模块里（而不是组件内）有两个好处：组件只负责调用；
 * 且 `Date.now()` / `window.location` 这类「非纯」操作不出现在 React 组件体中
 * （react-hooks 的 purity / immutability 规则会拦）。
 */
export const beginSsoRedirect = (provider: string, mode: 'login' | 'bind', url: string): void => {
  rememberSsoPending({ provider, mode, startedAt: Date.now() });
  window.location.assign(url);
};
