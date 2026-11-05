import type { MessageKey } from '@/i18n/messages/zh-CN';

/**
 * 已知第三方登录提供商（t106）。
 *
 * 为什么前端也维护一份「4 家」的清单：
 * - 登录页只能拿到 `GET /auth/sso/providers`（**已开启且已配置**），看不到「未启用」的；
 * - 个人中心要展示「已绑定 / 未绑定 / 未启用」三态，必须知道「本系统支持哪几家」，
 *   因此这里给出稳定顺序与 i18n 名称；后端返回的 `name` 优先用于展示（管理员可改名）。
 *
 * 与后端 `sso_providers` 初始 4 行（github/microsoft/google/wechat）一致；
 * 后端将来新增 provider 时，这里缺失的 code 仍会以后端 `name` 降级展示，不会丢数据。
 */
export interface KnownSsoProvider {
  code: string;
  labelKey: MessageKey;
}

export const KNOWN_SSO_PROVIDERS: KnownSsoProvider[] = [
  { code: 'github', labelKey: 'sso.provider.github' },
  { code: 'microsoft', labelKey: 'sso.provider.microsoft' },
  { code: 'google', labelKey: 'sso.provider.google' },
  { code: 'wechat', labelKey: 'sso.provider.wechat' },
];

export const knownSsoLabelKey = (code: string): MessageKey | null => {
  const hit = KNOWN_SSO_PROVIDERS.find((item) => item.code === code.trim().toLowerCase());
  return hit ? hit.labelKey : null;
};
