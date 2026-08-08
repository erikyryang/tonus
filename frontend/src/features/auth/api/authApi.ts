import { apiClient } from '@/lib/apiClient';

export type Role = 'ADMIN' | 'TEACHER' | 'STUDENT';

/** Mirrors the GET /auth/me DTO — the ids come from the token's claims. */
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
