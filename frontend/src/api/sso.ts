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
  /**
   * t116 起后端公开列表改为「**启用即返回**」，并用 `configured` 标记「5 项凭据是否齐全」。
   *
   * 因此登录页要**全部渲染**：`configured=false` 的按钮照常可点，
   * 但点击时前端直接提示「该登录方式尚未完成配置，请联系管理员」并**不跳转**
   * （后端 authorize-url 也会 503，这里提前拦是为了少一次 503 往返、提示更明确）。
   *
   * 缺省 `true`：老后端（t104 时期只返回已配置项）没有该字段时行为不变。
   */
  configured?: boolean;
}

export interface AdminSsoProvider extends SsoProvider {
  enabled: boolean;
  configured: boolean;
  usable: boolean;
  scopes?: string;
  /** t116：Web 配置回显（secret 只回掩码，永不回明文） */
  clientId?: string;
  clientSecretMasked?: string;
  authorizeUrl?: string;
  tokenUrl?: string;
  userinfoUrl?: string;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

/**
 * 管理员保存凭据的部分更新载荷（t116 `PUT /admin/sso/providers/{code}`）。
 *
 * - **未传字段保持不变**（所以这里全是可选）；
 * - `clientSecret` 省略/空串 = **不修改**；要清空必须显式 `clearClientSecret: true`；
 * - URL 必须 https（后端对 `http://localhost` / `http://127.0.0.1` 放宽，供本地联调）。
 */
export interface AdminSsoProviderUpdate {
  enabled?: boolean;
  clientId?: string;
  clientSecret?: string;
  clearClientSecret?: boolean;
  authorizeUrl?: string;
  tokenUrl?: string;
  userinfoUrl?: string;
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
        // 老后端没有 configured 字段 → 视为已配置（保持 t104 行为）
        configured: item.configured === undefined ? true : Boolean(item.configured),
      }));
  } catch {
    return [];
  }
};

/** 该 provider 是否因「凭据未配置」而不能真正跳转（缺省视为已配置） */
export const isProviderConfigured = (provider: SsoProvider | null | undefined): boolean =>
  provider?.configured !== false;

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

/** 把后端返回的 provider 归一（列表与保存响应共用；secret 只有掩码字段） */
export const normalizeAdminSsoProvider = (item: any, fallbackCode = ''): AdminSsoProvider => ({
  code: String(item?.code ?? fallbackCode),
  name: String(item?.name ?? item?.code ?? fallbackCode),
  icon: item?.icon ? String(item.icon) : undefined,
  enabled: Boolean(item?.enabled),
  configured: Boolean(item?.configured),
  usable: Boolean(item?.usable),
  scopes: item?.scopes ? String(item.scopes) : undefined,
  clientId: item?.clientId ? String(item.clientId) : undefined,
  clientSecretMasked: item?.clientSecretMasked ? String(item.clientSecretMasked) : undefined,
  authorizeUrl: item?.authorizeUrl ? String(item.authorizeUrl) : undefined,
  tokenUrl: item?.tokenUrl ? String(item.tokenUrl) : undefined,
  userinfoUrl: item?.userinfoUrl ? String(item.userinfoUrl) : undefined,
  updatedAt: item?.updatedAt ?? null,
  updatedBy: item?.updatedBy ?? null,
});

export const adminListSsoProviders = async (): Promise<AdminSsoProvider[]> => {
  const res = await request.get('/admin/sso/providers', { silent: true });
  const list = unwrap(res);
  return Array.isArray(list) ? (list as any[]).map((item) => normalizeAdminSsoProvider(item)) : [];
};

/**
 * 保存凭据（t116）：`PUT /admin/sso/providers/{code}` 部分更新。
 *
 * 关键约定（前端必须照做，否则会丢密钥）：
 * - **不传 `clientSecret`**（留空）→ 后端保持原有 secret 不变；
 * - 只有显式 `clearClientSecret: true` 才清空；
 * - 前端不做「把掩码当值提交」这种蠢事 —— 输入框留空即"不修改"。
 */
export const adminUpdateSsoProviderConfig = async (
  code: string,
  payload: AdminSsoProviderUpdate,
): Promise<AdminSsoProvider> => {
  const res = await request.put(`/admin/sso/providers/${encodeURIComponent(code)}`, payload, { silent: true });
  return normalizeAdminSsoProvider(unwrap(res) ?? {}, code);
};

export const adminToggleSsoProvider = async (code: string, enabled: boolean): Promise<AdminSsoProvider> => {
  const res = await request.patch(
    `/admin/sso/providers/${encodeURIComponent(code)}`,
    { enabled },
    { silent: true },
  );
  return normalizeAdminSsoProvider(unwrap(res) ?? {}, code);
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
