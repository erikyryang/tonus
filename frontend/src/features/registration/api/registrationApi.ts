import { apiClient } from '@/lib/apiClient';
import type { Page } from '@/lib/page';

/** Espelha o DTO de /students. */
export interface Student {
  id: number;
  name: string;
  phone: string;
  teacherId: number;
  active: boolean;
}

/** Espelha o DTO de /teachers. */
export interface Teacher {
  id: number;
  name: string;
  phone: string;
  active: boolean;
}

export interface StudentInput {
  name: string;
  phone: string;
  teacherId: number;
}

export interface TeacherInput {
  name: string;
  phone: string;
}

/** `active` ausente é "todos" — é assim que o back lê o parâmetro que não veio. */
export interface PeopleQuery {
  active?: boolean;
  page: number;
}

/**
 * Uma página grande o bastante para caber o estúdio inteiro.
 *
 * As listas de apoio (nome do professor na coluna de alunos, opções dos
 * selects) precisam de todo mundo de uma vez: paginá-las faria o select
 * esconder gente sem dizer que escondeu, e a coluna mostrar "Professor #3" para
 * quem só não coube na primeira página.
 */
const LOOKUP_SIZE = 200;

/**
 * Sem argumento devolve o prefixo — é por ele que uma mutação invalida todas as
 * combinações de filtro e página de uma vez, inclusive a lista de apoio.
 */
export const studentsKey = (query?: PeopleQuery) =>
  query ? (['registration', 'students', query] as const) : (['registration', 'students'] as const);

export const teachersKey = (query?: PeopleQuery) =>
  query ? (['registration', 'teachers', query] as const) : (['registration', 'teachers'] as const);

export const studentsLookupKey = ['registration', 'students', 'lookup'] as const;
export const teachersLookupKey = ['registration', 'teachers', 'lookup'] as const;

function listParams({ active, page }: PeopleQuery): URLSearchParams {
  const params = new URLSearchParams({ page: String(page) });
  if (active !== undefined) params.set('active', String(active));
  return params;
}

const LOOKUP_PARAMS = new URLSearchParams({ page: '0', size: String(LOOKUP_SIZE) });

export const registrationApi = {
  students: (query: PeopleQuery) => apiClient.get<Page<Student>>(`/students?${listParams(query)}`),
  /** Todos, ativos e inativos: quem só está na lista para dar nome a um id também saiu do estúdio. */
  studentsLookup: () => apiClient.get<Page<Student>>(`/students?${LOOKUP_PARAMS}`),
  createStudent: (input: StudentInput) => apiClient.post<Student>('/students', input),
  updateStudent: (id: number, input: StudentInput) => apiClient.put<Student>(`/students/${id}`, input),
  /** Não existe DELETE de aluno no back: saída do estúdio é desativação. */
  deactivateStudent: (id: number) => apiClient.patch<Student>(`/students/${id}/deactivate`),
  activateStudent: (id: number) => apiClient.patch<Student>(`/students/${id}/activate`),

  teachers: (query: PeopleQuery) => apiClient.get<Page<Teacher>>(`/teachers?${listParams(query)}`),
  teachersLookup: () => apiClient.get<Page<Teacher>>(`/teachers?${LOOKUP_PARAMS}`),
  createTeacher: (input: TeacherInput) => apiClient.post<Teacher>('/teachers', input),
  updateTeacher: (id: number, input: TeacherInput) => apiClient.put<Teacher>(`/teachers/${id}`, input),
  deactivateTeacher: (id: number) => apiClient.patch<Teacher>(`/teachers/${id}/deactivate`),
  activateTeacher: (id: number) => apiClient.patch<Teacher>(`/teachers/${id}/activate`),
};
