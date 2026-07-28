import { apiClient } from '@/lib/apiClient';

export type Role = 'ADMIN' | 'TEACHER' | 'STUDENT';

/** Espelha o DTO de GET /auth/me — os ids vêm dos claims do token. */
export interface SessionUser {
  role: Role;
  name: string;
  studentId?: number;
  teacherId?: number;
}

export interface LoginRequest {
  login: string;
  password: string;
}

export const sessionQueryKey = ['auth', 'me'] as const;

export const authApi = {
  login: (request: LoginRequest) => apiClient.post<void>('/auth/login', request),
  logout: () => apiClient.post<void>('/auth/logout'),
  me: () => apiClient.get<SessionUser>('/auth/me'),
};
