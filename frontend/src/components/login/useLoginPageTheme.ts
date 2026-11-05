import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getPublicLoginThemeConfig } from '@/api/auth';
import { getLoginTheme } from '@/components/login/themes';
import { DEFAULT_LOGIN_THEME, type LoginThemeId } from '@/components/login/types';
import type { LoginThemeEntry } from '@/components/login/themes';
import {
  readCachedDefaultLoginTheme,
  readLocalLoginThemeMark,
  resolveLoginTheme,
  writeCachedDefaultLoginTheme,
} from '@/utils/loginTheme';

/**
 * 登录族页面的主题解析（t105 从 `pages/Login.tsx` 抽出，供登录 / 注册 / 忘记密码共用）。
 *
 * 优先级（t110 更正版）：`?theme=`（仅预览、不写 storage）> 本机标记 `localStorage('login.theme')`
 * > 管理员缺省（公开端点 `GET /auth/login-theme`，免认证）> `pencil`。
 *
 * - 认证前无法调用需要登录的偏好接口，本机标记 + 公开缺省就是唯一可用来源；
 * - 公开端点失败/超时都**静默降级**（保留 sessionStorage 里的 5 分钟缓存或 pencil），
 *   绝不阻塞登录/注册/重置页面渲染（也不弹提示）。
 */
export interface LoginPageTheme {
  theme: LoginThemeId;
  entry: LoginThemeEntry;
}

export const useLoginPageTheme = (): LoginPageTheme => {
  const [searchParams] = useSearchParams();
  const themeParam = searchParams.get('theme');
  const [adminDefault, setAdminDefault] = useState<LoginThemeId>(
    () => readCachedDefaultLoginTheme()?.theme ?? DEFAULT_LOGIN_THEME,
  );

  useEffect(() => {
    let cancelled = false;
    getPublicLoginThemeConfig()
      .then((config) => {
        if (cancelled) return;
        setAdminDefault(config.defaultTheme);
        writeCachedDefaultLoginTheme(config.defaultTheme);
      })
      .catch(() => {
        // 静默降级：保留会话缓存 / pencil，页面照常可用
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const theme = useMemo(
    () =>
      resolveLoginTheme({
        previewParam: themeParam,
        localMark: readLocalLoginThemeMark(),
        adminDefault,
      }).theme,
    [themeParam, adminDefault],
  );

  const entry = useMemo(() => getLoginTheme(theme), [theme]);

  return { theme, entry };
};
