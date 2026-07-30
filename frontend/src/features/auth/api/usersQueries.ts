import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type AccessUser, type CreateAccessInput, accessKey, accessOfStudentsKey, usersApi } from './usersApi';

/**
 * O acesso de cada aluno da página, indexado pelo id do aluno. É o que decide
 * qual ação a linha oferece: nenhuma (login ativo), "Criar acesso" (nunca teve)
 * ou "Reativar acesso" (teve e foi revogado).
 */
export function useAccessOfStudents(studentIds: number[]) {
  return useQuery({
    queryKey: accessOfStudentsKey(studentIds),
    queryFn: () => usersApi.accessOfStudents(studentIds),
    // Página vazia não tem o que perguntar — e `studentIds=` sem valor seria
    // uma requisição sem sentido para o back recusar.
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
