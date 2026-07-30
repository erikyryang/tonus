import { apiClient } from '@/lib/apiClient';
import type { Role } from './authApi';

/**
 * O usuário de acesso de um aluno ou professor. Espelha o DTO de /users —
 * nunca traz senha, e `active` é o que distingue "sem acesso" de "acesso
 * revogado".
 */
export interface AccessUser {
  id: number;
  login: string;
  role: Role;
  studentId: number | null;
  teacherId: number | null;
  active: boolean;
}

export interface CreateAccessInput {
  login: string;
  password: string;
  studentId: number;
}

/**
 * Uma chamada por página de alunos, e não uma por linha: a tela precisa saber
 * de quem já tem login para escolher entre "Criar acesso" e "Reativar acesso",
 * e vinte requisições para montar vinte botões seria absurdo.
 */
export const accessOfStudentsKey = (studentIds: number[]) =>
  ['auth', 'users', 'byStudents', [...studentIds].sort((a, b) => a - b)] as const;

/** Prefixo para invalidar qualquer combinação depois de criar ou reativar acesso. */
export const accessKey = ['auth', 'users'] as const;

export const usersApi = {
  accessOfStudents: (studentIds: number[]) =>
    apiClient.get<AccessUser[]>(`/users?studentIds=${studentIds.join(',')}`),
  /** O papel é consequência do vínculo, não escolha da tela: aluno recebe login de aluno. */
  createStudentAccess: ({ login, password, studentId }: CreateAccessInput) =>
    apiClient.post<AccessUser>('/users', { login, password, role: 'STUDENT', studentId }),
  /**
   * Reativar é manual por design: revogar acontece sozinho quando o aluno é
   * desativado (evento), conceder de novo é decisão de quem administra.
   */
  activateAccess: (id: number) => apiClient.patch<AccessUser>(`/users/${id}/activate`),
};
