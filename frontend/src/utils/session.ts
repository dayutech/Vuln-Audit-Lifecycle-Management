/**
 * 会话生命周期标记（t122，对应「登出后误报未认证」）。
 *
 * 场景：点「退出登录」→ 立刻清 token + 跳 /login。此时**在飞请求**（仪表盘轮询、
 * 通知未读数等）还会带着刚才的 token 打到后端，后端返回 401；
 * 若 401 拦截器照常弹「未认证，请先登录」并再跳一次登录页，用户就会看到
 * 「刚点完退出，页面上又冒出一条未认证错误」。
 *
 * 这里用一个模块级标记表达「正在登出」的短窗口：
 * - `markLoggingOut()`：登出开始时置位，并在 `LOGOUT_WINDOW_MS` 后自动失效
 *   （避免用户之后正常使用时 401 被永久静默）；
 * - `isLoggingOut()`：401 拦截器据此**不弹提示、不重复跳转**；
 * - `endLoggingOut()`：跳转到登录页后立即收尾。
 *
 * 纯内存标记，不做持久化：刷新页面即复位，符合「一次性过渡态」的语义。
 */
const LOGOUT_WINDOW_MS = 3000;

let loggingOut = false;
let timer: number | null = null;

const clearTimer = (): void => {
  if (timer !== null) {
    window.clearTimeout(timer);
    timer = null;
  }
};

export const markLoggingOut = (): void => {
  loggingOut = true;
  if (typeof window === 'undefined') return;
  clearTimer();
  timer = window.setTimeout(() => {
    loggingOut = false;
    timer = null;
  }, LOGOUT_WINDOW_MS);
};

export const endLoggingOut = (): void => {
  loggingOut = false;
  clearTimer();
};

export const isLoggingOut = (): boolean => loggingOut;

/** 当前是否已经在登录页（登录失败/会话过期都不应该再弹提示或重复跳转） */
export const isOnLoginPage = (): boolean => {
  if (typeof window === 'undefined') return false;
  return window.location.pathname === '/login';
};
