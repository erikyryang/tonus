import { http, HttpResponse } from 'msw';
import type { SessionUser } from '@/features/auth/api/authApi';

export const ADMIN: SessionUser = { role: 'ADMIN', name: 'Ana Admin' };
export const STUDENT: SessionUser = { role: 'STUDENT', name: 'Bia Aluna', studentId: 10 };
export const TEACHER: SessionUser = { role: 'TEACHER', name: 'Caio Professor', teacherId: 20 };

export function meOk(user: SessionUser) {
  return http.get('/api/auth/me', () => HttpResponse.json(user));
}

export function meUnauthorized() {
  return http.get('/api/auth/me', () => unauthorized('Não autenticado'));
}

export function loginOk() {
  return http.post('/api/auth/login', () => new HttpResponse(null, { status: 200 }));
}

export function loginInvalid() {
  return http.post('/api/auth/login', () => unauthorized('Credenciais inválidas'));
}

export function logoutOk() {
  return http.post('/api/auth/logout', () => new HttpResponse(null, { status: 200 }));
}

export function refreshFails() {
  return http.post('/api/auth/refresh', () => unauthorized('Refresh token inválido ou reutilizado'));
}

export function unauthorized(detail: string) {
  return HttpResponse.json(
    { type: 'about:blank', title: 'Unauthorized', status: 401, detail },
    { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
  );
}
