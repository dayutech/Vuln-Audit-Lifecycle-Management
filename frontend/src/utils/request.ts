import axios from 'axios';
import type { InternalAxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';
import { message } from 'antd';
import { translate } from '@/i18n';
import { attachAcceptLanguageHeader } from './langHeader';
import { endImpersonation, readImpersonationTarget } from '@/utils/impersonation';
import { isLoggingOut, isOnLoginPage } from '@/utils/session';

declare module 'axios' {
  interface AxiosRequestConfig {
    /**
     * 关闭全局错误提示：由调用方按状态码自行提示（环境部署 503、附件下载 403/404 等）。
     * 401 的登录跳转不受此开关影响。
     */
    silent?: boolean;
  }
}

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
});

/**
 * 请求拦截器：鉴权头 + Accept-Language（t64）。
 *
 * Accept-Language 每次请求实时读取 localStorage('app.lang')，
 * 因此切到 English 后**无需刷新**，后端 t60 的 message 就会按英文返回；
 * 调用方显式传入的 Accept-Language 不会被覆盖（见 utils/langHeader）。
 */
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('access_token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    attachAcceptLanguageHeader(config);
    return config;
  },
  (error: AxiosError) => {
    return Promise.reject(error);
  }
);

/**
 * 全局 axios 拦截器：覆盖少量「裸 axios」调用
 * （api/notification 的服务端时钟、api/tool 与 api/vulnerability 的 blob 下载，
 *  它们为了读取响应头而绕过 request 实例）。实例拦截器不继承全局拦截器，故两处都要挂。
 */
axios.interceptors.request.use((config) => attachAcceptLanguageHeader(config));

// Response interceptor
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data;
    // Backend API sometimes returns direct objects (like 200 LoginResponse) 
    // or standard ApiResponse depending on the endpoint.
    // We will return the raw data and let API services handle their typing.
    return res;
  },
  (error: AxiosError) => {
    /**
     * silent: 调用方显式传 { silent: true } 时由调用方自己提示（例如环境部署 503、
     * 附件下载 403/404 需要展示后端 message + 失败原因），避免和全局提示重复。
     * 401 仍统一跳登录，不受 silent 影响。
     *
     * 兜底文案走 i18n（translate 实时读取当前语言）：后端 message 存在时原样展示
     * （t60 起后端会按 Accept-Language 返回中/英文），不存在时才用本地兜底。
     */
    const silent = Boolean((error.config as { silent?: boolean } | undefined)?.silent);
    if (error.response) {
      const { status, data } = error.response as any;
      const errorMsg = data?.message || data?.error?.message;
      if (status === 401) {
        /**
         * t122（对应意见 18「登出后误报未认证」）：401 一律**不弹提示**，只做
         * 「清 token + 必要时回登录页」。原因：
         * 1) 主动登出时在飞请求会 401 —— 用户刚点退出，不该再看到「未认证」；
         * 2) 会话过期被动登出 / 后台标签页 token 失效 —— 直接回登录页即可；
         * 3) 登录页本身输错密码也是 401 —— 页面已有行内错误，再弹一条是重复。
         */
        if (isLoggingOut()) {
          return Promise.reject(error);
        }
        // t91：模拟态 token 过期/失效 → 自动回退到原始 admin token，而不是踢去登录页
        const target = readImpersonationTarget();
        if (target) {
          const original = endImpersonation();
          if (original?.token) {
            window.location.reload();
            return Promise.reject(error);
          }
          window.location.href = '/login';
          return Promise.reject(error);
        }
        localStorage.removeItem('access_token');
        /**
         * 已经在登录页时**不要再整页跳转**（t95）：
         * 登录失败本身就是 401，跳转会打断页面把「错误态」呈现给用户
         * （角色抖动 + 行内错误提示），并让用户重填一遍表单。
         */
        if (!isOnLoginPage()) {
          window.location.href = '/login';
        }
        return Promise.reject(error);
      }
      if (!silent) {
        switch (status) {
          case 403:
            /**
             * t137：强制两步验证拦截（后端 `TwoFactorEnforcementInterceptor`）。
             *
             * 这类 403 不是"没权限"，而是"还没开通 2FA"——
             * 派发全局事件让 `TwoFactorRequiredBanner` 展示引导，并且**不弹**通用的「无权限」toast，
             * 否则用户会看到一条误导性的报错。后端已把 `/auth/**`、`/users/me/2fa`、`/users/me/preferences`
             * 加入白名单，因此设置页与登出不会被这条引导循环拦截。
             */
            if ((data as any)?.code === 'TWO_FACTOR_SETUP_REQUIRED') {
              window.dispatchEvent(new CustomEvent('twoFactor:setup-required'));
              break;
            }
            message.error(errorMsg || translate('error.forbidden'));
            break;
          case 400:
            message.error(errorMsg || translate('error.badRequest'));
            break;
          case 404:
            message.error(errorMsg || translate('error.notFound'));
            break;
          case 500:
            message.error(errorMsg || translate('error.server'));
            break;
          case 503:
            message.error(errorMsg || translate('error.unavailable'));
            break;
          default:
            message.error(errorMsg || translate('error.unknown'));
        }
      }
    } else if (!silent) {
      message.error(translate('error.network'));
    }
    return Promise.reject(error);
  }
);

export default request;
