import { create } from 'zustand';
import type { UserVO } from '@/types/auth';

interface UserState {
  user: UserVO | null;
  token: string | null;
  setToken: (token: string) => void;
  setUser: (user: UserVO | null) => void;
  clearAuth: () => void;
}

export const useUserStore = create<UserState>((set) => ({
  user: null,
  token: localStorage.getItem('access_token'),
  setToken: (token) => {
    localStorage.setItem('access_token', token);
    set({ token });
  },
  setUser: (user) => set({ user }),
  clearAuth: () => {
    localStorage.removeItem('access_token');
    set({ user: null, token: null });
  },
}));
