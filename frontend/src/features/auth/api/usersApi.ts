import { apiClient } from '@/lib/apiClient';
import type { Role } from './authApi';

/**
 * The access user of a student or teacher. Mirrors the /users DTO —
 * never carries a password, and `active` is what distinguishes "no access" from "access
 * revoked".
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
 * One call per page of students, not one per row: the screen needs to know
 * who already has a login to choose between "Create access" and "Reactivate access",
 * and twenty requests to build twenty buttons would be absurd.
 */
export const accessOfStudentsKey = (studentIds: number[]) =>
  ['auth', 'users', 'byStudents', [...studentIds].sort((a, b) => a - b)] as const;

/** Prefix to invalidate any combination after creating or reactivating access. */
export const accessKey = ['auth', 'users'] as const;

export const usersApi = {
  accessOfStudents: (studentIds: number[]) =>
    apiClient.get<AccessUser[]>(`/users?studentIds=${studentIds.join(',')}`),
  /** The role is a consequence of the relationship, not a screen choice: a student gets a student login. */
  createStudentAccess: ({ login, password, studentId }: CreateAccessInput) =>
    apiClient.post<AccessUser>('/users', { login, password, role: 'STUDENT', studentId }),
  /**
   * Reactivating is manual by design: revoking happens automatically when the student is
   * deactivated (event), granting it again is the administrator's decision.
   */
  activateAccess: (id: number) => apiClient.patch<AccessUser>(`/users/${id}/activate`),
};
