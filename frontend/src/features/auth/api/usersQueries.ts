import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type AccessUser, type CreateAccessInput, accessKey, accessOfStudentsKey, usersApi } from './usersApi';

/**
 * The access of each student on the page, indexed by student id. It's what decides
 * which action the row offers: none (active login), "Create access" (never had one)
 * or "Reactivate access" (had one and it was revoked).
 */
export function useAccessOfStudents(studentIds: number[]) {
  return useQuery({
    queryKey: accessOfStudentsKey(studentIds),
    queryFn: () => usersApi.accessOfStudents(studentIds),
    // An empty page has nothing to ask — and `studentIds=` with no value would be
    // a nonsensical request for the back to reject.
    enabled: studentIds.length > 0,
    select: (users: AccessUser[]) =>
      new Map(users.filter((user) => user.studentId != null).map((user) => [user.studentId!, user])),
  });
}

function useAccessInvalidation() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: accessKey });
}

export function useCreateStudentAccess() {
  const invalidate = useAccessInvalidation();
  return useMutation({
    mutationFn: (input: CreateAccessInput) => usersApi.createStudentAccess(input),
    onSuccess: invalidate,
  });
}

export function useActivateAccess() {
  const invalidate = useAccessInvalidation();
  return useMutation({
    mutationFn: (id: number) => usersApi.activateAccess(id),
    onSuccess: invalidate,
  });
}
