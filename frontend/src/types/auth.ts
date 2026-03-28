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
}
