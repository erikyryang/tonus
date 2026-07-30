import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { accessKey } from '@/features/auth/api/usersApi';
import {
  type PeopleQuery,
  type Student,
  type StudentInput,
  type Teacher,
  type TeacherInput,
  registrationApi,
  studentsKey,
  studentsLookupKey,
  teachersKey,
  teachersLookupKey,
} from './registrationApi';

export function useStudents(query: PeopleQuery) {
  return useQuery({
    queryKey: studentsKey(query),
    queryFn: () => registrationApi.students(query),
    /** Trocar de filtro ou de página não pisca a tabela para branco. */
    placeholderData: keepPreviousData,
  });
}

export function useTeachers(query: PeopleQuery) {
  return useQuery({
    queryKey: teachersKey(query),
    queryFn: () => registrationApi.teachers(query),
    placeholderData: keepPreviousData,
  });
}

/**
 * A lista de apoio: dá nome ao id na coluna e alimenta os selects. Vem inteira
 * (ativos e inativos) porque as duas necessidades são diferentes — o select
 * mostra só quem está ativo, mas a coluna precisa nomear quem já saiu.
 */
export function useStudentsLookup() {
  return useQuery({
    queryKey: studentsLookupKey,
    queryFn: registrationApi.studentsLookup,
    select: (page) => page.content,
  });
}

export function useTeachersLookup() {
  return useQuery({
    queryKey: teachersLookupKey,
    queryFn: registrationApi.teachersLookup,
    select: (page) => page.content,
  });
}

/** Índice id → nome, para a coluna que só recebe o id do back. */
export function nameById<T extends { id: number; name: string }>(people: T[] | undefined): Map<number, string> {
  return new Map((people ?? []).map((person) => [person.id, person.name]));
}

function useStudentInvalidation() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: studentsKey() });
}

export function useCreateStudent() {
  const invalidate = useStudentInvalidation();
  return useMutation({
    mutationFn: (input: StudentInput) => registrationApi.createStudent(input),
    onSuccess: invalidate,
  });
}

export interface UpdateStudentInput {
  id: number;
  input: StudentInput;
}

export function useUpdateStudent() {
  const invalidate = useStudentInvalidation();
  return useMutation({
    mutationFn: ({ id, input }: UpdateStudentInput) => registrationApi.updateStudent(id, input),
    onSuccess: invalidate,
  });
}

/**
 * Desativar o aluno derruba o login dele no back, por evento. A lista de acesso
 * em cache passou a mentir junto com a de alunos, então as duas são
 * invalidadas — mostrar "Reativar acesso" ou não depende disso.
 */
export function useSetStudentActive() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }): Promise<Student> =>
      active ? registrationApi.activateStudent(id) : registrationApi.deactivateStudent(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: studentsKey() });
      await queryClient.invalidateQueries({ queryKey: accessKey });
    },
  });
}

function useTeacherInvalidation() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: teachersKey() });
}

export function useCreateTeacher() {
  const invalidate = useTeacherInvalidation();
  return useMutation({
    mutationFn: (input: TeacherInput) => registrationApi.createTeacher(input),
    onSuccess: invalidate,
  });
}

export interface UpdateTeacherInput {
  id: number;
  input: TeacherInput;
}

export function useUpdateTeacher() {
  const invalidate = useTeacherInvalidation();
  return useMutation({
    mutationFn: ({ id, input }: UpdateTeacherInput) => registrationApi.updateTeacher(id, input),
    onSuccess: invalidate,
  });
}

export function useSetTeacherActive() {
  const invalidate = useTeacherInvalidation();
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }): Promise<Teacher> =>
      active ? registrationApi.activateTeacher(id) : registrationApi.deactivateTeacher(id),
    onSuccess: invalidate,
  });
}
