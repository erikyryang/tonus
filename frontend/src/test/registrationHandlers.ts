import { http, HttpResponse } from 'msw';
import type {
  Student,
  StudentInput,
  Teacher,
  TeacherInput,
} from '@/features/registration/api/registrationApi';
import type { Page } from '@/lib/page';
import { pageOf } from './pageOf';

export function student(overrides: Partial<Student> & { id: number }): Student {
  return {
    name: `Aluno ${overrides.id}`,
    phone: '+5511999990000',
    teacherId: 10,
    active: true,
    ...overrides,
  };
}

export function teacher(overrides: Partial<Teacher> & { id: number }): Teacher {
  return {
    name: `Professor ${overrides.id}`,
    phone: '+5511988880000',
    active: true,
    ...overrides,
  };
}

/**
 * A listagem vem de uma função para que o teste possa trocar a resposta depois
 * de uma mutação — é assim que se observa "invalidou e refez a chamada" sem
 * espiar o TanStack Query por dentro.
 *
 * O mesmo handler atende a listagem da tabela e a lista de apoio (size grande,
 * que alimenta a coluna de professor e os selects): as duas batem em
 * GET /students, e é o resolver do teste que decide o que cada uma recebe.
 *
 * `log` recebe a query string inteira: filtro que não chega ao backend não é
 * filtro.
 */
export function studentsHandler(resolve: (params: URLSearchParams) => Page<Student>, log?: string[]) {
  return http.get('/api/students', ({ request }) => {
    const url = new URL(request.url);
    log?.push(url.search);
    return HttpResponse.json(resolve(url.searchParams));
  });
}

export function teachersHandler(resolve: (params: URLSearchParams) => Page<Teacher>, log?: string[]) {
  return http.get('/api/teachers', ({ request }) => {
    const url = new URL(request.url);
    log?.push(url.search);
    return HttpResponse.json(resolve(url.searchParams));
  });
}

/** Lista fixa de alunos, qualquer que seja o filtro — para os testes de render. */
export function studentsList(...students: Student[]) {
  return studentsHandler(() => pageOf(students));
}

export function teachersList(...teachers: Teacher[]) {
  return teachersHandler(() => pageOf(teachers));
}

export function createStudentOk(onRequest?: (body: StudentInput) => void, id = 99) {
  return http.post('/api/students', async ({ request }) => {
    const body = (await request.json()) as StudentInput;
    onRequest?.(body);
    return HttpResponse.json(student({ id, ...body }), { status: 201 });
  });
}

export function updateStudentOk(onRequest?: (id: number, body: StudentInput) => void) {
  return http.put('/api/students/:id', async ({ params, request }) => {
    const body = (await request.json()) as StudentInput;
    const id = Number(params.id);
    onRequest?.(id, body);
    return HttpResponse.json(student({ id, ...body }));
  });
}

export function deactivateStudentOk(onRequest?: (id: number) => void) {
  return http.patch('/api/students/:id/deactivate', ({ params }) => {
    const id = Number(params.id);
    onRequest?.(id);
    return HttpResponse.json(student({ id, active: false }));
  });
}

export function activateStudentOk(onRequest?: (id: number) => void) {
  return http.patch('/api/students/:id/activate', ({ params }) => {
    const id = Number(params.id);
    onRequest?.(id);
    return HttpResponse.json(student({ id, active: true }));
  });
}

export function createTeacherOk(onRequest?: (body: TeacherInput) => void, id = 98) {
  return http.post('/api/teachers', async ({ request }) => {
    const body = (await request.json()) as TeacherInput;
    onRequest?.(body);
    return HttpResponse.json(teacher({ id, ...body }), { status: 201 });
  });
}

export function updateTeacherOk(onRequest?: (id: number, body: TeacherInput) => void) {
  return http.put('/api/teachers/:id', async ({ params, request }) => {
    const body = (await request.json()) as TeacherInput;
    const id = Number(params.id);
    onRequest?.(id, body);
    return HttpResponse.json(teacher({ id, ...body }));
  });
}

export function deactivateTeacherOk(onRequest?: (id: number) => void) {
  return http.patch('/api/teachers/:id/deactivate', ({ params }) => {
    const id = Number(params.id);
    onRequest?.(id);
    return HttpResponse.json(teacher({ id, active: false }));
  });
}
