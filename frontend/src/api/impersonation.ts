import request from '@/utils/request';

/**
 * 管理员模拟视角（t91，对接后端 t90）。
 *
 * - POST /admin/impersonate      仅 ADMIN；body {userId, reason?}
 *   → {token, expiresAt, target{id,username,roles[]}, actor{id,username}}
 * - POST /admin/impersonate/stop 普通态要求 ADMIN；模拟态也允许 → 204
 *
 * 模拟 token 的限制（后端已强制，前端只做入口收敛）：
 * - 不能链式模拟（/admin/** 写方法在模拟态一律 403）
 * - 模拟态下 /users/**、/roles/** 写操作 403
 * - 审计记录 impersonatedBy
 */
export interface ImpersonateTarget {
  id: string;
  username: string;
  roles: string[];
}

export interface ImpersonateActor {
  id: string;
  username: string;
}

export interface ImpersonateResponse {
  token: string;
  /** ISO 时间戳（后端 Instant） */
  expiresAt: string;
  target: ImpersonateTarget;
  actor: ImpersonateActor;
}

/** 发起模拟：目标不存在 404、目标停用 400、目标是 ADMIN 409、非 ADMIN 403 */
export const startImpersonation = async (userId: string, reason?: string): Promise<ImpersonateResponse> => {
  const res: any = await request.post(
    '/admin/impersonate',
    { userId, reason: reason || undefined },
    { silent: true },
  );
  // 拦截器返回的是 ApiResponse 信封，这里统一解包 data
  const payload = (res?.data ?? res) as ImpersonateResponse;
  if (!payload?.token) throw new Error('impersonate response missing token');
  return payload;
};

/** 停止模拟（204）；失败也由调用方继续恢复本地 token */
export const stopImpersonation = (): Promise<any> => {
  return request.post('/admin/impersonate/stop', null, { silent: true });
};
