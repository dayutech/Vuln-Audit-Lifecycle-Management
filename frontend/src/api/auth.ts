import request from '@/utils/request';

export const login = (data: any): Promise<any> => {
  return request.post('/auth/login', data);
};

export const logout = (): Promise<any> => {
  return request.post('/auth/logout');
};

export const getCurrentUser = (): Promise<any> => {
  return request.get('/auth/me');
};
