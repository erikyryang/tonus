import { http, HttpResponse } from 'msw';
import type { AccessUser, CreateAccessInput } from '@/features/auth/api/usersApi';

export function accessUser(overrides: Partial<AccessUser> & { id: number; studentId: number }): AccessUser {
  return {
    login: `aluno${overrides.studentId}`,
    role: 'STUDENT',
    teacherId: null,
    active: true,
    ...overrides,
  };
}

/**
 * GET /users?studentIds=… — a consulta usuário-por-vínculo que a tela de alunos
 * precisa para escolher entre "Criar acesso" e "Reativar acesso". Sem ela o
 * front não teria o id do usuário para reativar (SPEC-009).
 */
export function accessOfStudentsHandler(resolve: (studentIds: number[]) => AccessUser[], log?: string[]) {
  return http.get('/api/users', ({ request }) => {
    const raw = new URL(request.url).searchParams.get('studentIds') ?? '';
    log?.push(raw);
    const ids = raw
      .split(',')
      .filter((value) => value.length > 0)
      .map(Number);
    return HttpResponse.json(resolve(ids));
  });
}

/** Nenhum aluno tem acesso ainda — o estado de quem acabou de cadastrar. */
export function noAccessYet() {
  return accessOfStudentsHandler(() => []);
}

export function createAccessOk(onRequest?: (body: CreateAccessInput & { role: string }) => void, id = 500) {
  return http.post('/api/users', async ({ request }) => {
    const body = (await request.json()) as CreateAccessInput & { role: string };
    onRequest?.(body);
    return HttpResponse.json(accessUser({ id, studentId: body.studentId, login: body.login }), { status: 201 });
  });
}

export function activateAccessOk(onRequest?: (id: number) => void) {
  return http.patch('/api/users/:id/activate', ({ params }) => {
    const id = Number(params.id);
    onRequest?.(id);
    return HttpResponse.json(accessUser({ id, studentId: 0, active: true }));
  });
}
