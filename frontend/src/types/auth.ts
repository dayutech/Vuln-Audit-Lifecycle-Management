export interface UserVO {
  id: string;
  username: string;
  email: string;
  phone?: string;
  full_name: string;
  role: string;
  avatar?: string;
  teams?: Array<{
    id: string;
    name: string;
    role: string;
  }>;
  created_at: string;
  last_login?: string;
}

export interface LoginResponse {
  access_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
  user: UserVO;
  /**
   * 登录页主题偏好（t96 起由 `POST /auth/login` / `GET /auth/me` / `POST /auth/refresh` 携带）。
   * 取值是 `LoginThemeId` 的字符串形式；旧后端不返回该字段时为 undefined（前端保留本机缓存）。
   */
  loginTheme?: string;
}
