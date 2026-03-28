import axios from 'axios';
import type { InternalAxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';
import { message } from 'antd';

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
});

// Request interceptor
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('access_token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error: AxiosError) => {
    return Promise.reject(error);
  }
);

// Response interceptor
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data;
    // Backend API sometimes returns direct objects (like 200 LoginResponse) 
    // or standard ApiResponse depending on the endpoint.
    // We will return the raw data and let API services handle typing.
    return res;
  },
  (error: AxiosError) => {
    if (error.response) {
      const { status, data } = error.response as any;
      switch (status) {
        case 401:
          message.error('登录已过期，请重新登录');
          localStorage.removeItem('access_token');
          window.location.href = '/login';
          break;
        case 403:
          message.error('没有操作权限');
          break;
        case 400:
          message.error(data?.error?.message || '请求参数错误');
          break;
        case 404:
          message.error(data?.error?.message || '资源不存在');
          break;
        case 500:
          message.error(data?.error?.message || '服务器内部错误');
          break;
        default:
          message.error(data?.error?.message || '网络错误');
      }
    } else {
      message.error('网络请求失败');
    }
    return Promise.reject(error);
  }
);

export default request;
