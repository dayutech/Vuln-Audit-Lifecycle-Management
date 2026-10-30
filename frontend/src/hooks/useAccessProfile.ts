import { useEffect, useState } from 'react';
import { getMyAccessProfile } from '@/api/permission';
import type { MyAccessProfile } from '@/utils/accessRoles';

/**
 * 读取当前账号的角色/权限画像（`GET /users/me/permissions`，所有登录角色可读）。
 *
 * - 返回 `loaded` 之前不要渲染与角色相关的写入口，避免「先显示再消失」的闪烁；
 * - 画像请求失败（离线/未登录）时 `profile` 保持 null：能力判定一律按 fail-closed 处理，
 *   由页面自身的 403 提示兜底。
 */
export const useAccessProfile = (): { profile: MyAccessProfile | null; loaded: boolean } => {
  const [profile, setProfile] = useState<MyAccessProfile | null>(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getMyAccessProfile()
      .then((value) => {
        if (!cancelled) setProfile(value);
      })
      .finally(() => {
        if (!cancelled) setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return { profile, loaded };
};

export default useAccessProfile;
