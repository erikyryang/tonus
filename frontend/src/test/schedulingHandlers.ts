import { http, HttpResponse } from 'msw';
import type {
  DaySchedule,
  DayScheduleCancellation,
  DayScheduleEntry,
  ScheduleExceptionRequest,
} from '@/features/scheduling/api/schedulingApi';

export const MONDAY = '2026-07-27';
export const WEDNESDAY = '2026-07-29';
export const THURSDAY = '2026-07-30';
export const SATURDAY = '2026-08-01';
export const HOLIDAY = '2026-09-07';

export function entry(overrides: Partial<DayScheduleEntry> & { studentId: number }): DayScheduleEntry {
  return {
    studentName: `Aluno ${overrides.studentId}`,
    teacherId: 10,
    teacherName: 'Bruno Braga',
    time: '07:00',
    origin: 'TEMPLATE',
    ...overrides,
  };
}

export function cancellation(
  overrides: Partial<DayScheduleCancellation> & { studentId: number; exceptionId: number },
): DayScheduleCancellation {
  return {
    studentName: `Aluno ${overrides.studentId}`,
    teacherId: 10,
    teacherName: 'Bruno Braga',
    time: '07:00',
    ...overrides,
  };
}

export function day(date: string, overrides: Partial<DaySchedule> = {}): DaySchedule {
  return { date, entries: [], cancellations: [], ...overrides };
}

/**
 * O dia vem de uma função para que o teste possa trocar a resposta depois de
 * uma mutação — é assim que se observa "invalidou e refez a chamada" sem
 * espiar o TanStack Query por dentro.
 */
export function dayScheduleHandler(resolve: (date: string) => DaySchedule, log?: string[]) {
  return http.get('/api/schedule/day', ({ request }) => {
    const date = new URL(request.url).searchParams.get('date') ?? '';
    log?.push(date);
    return HttpResponse.json(resolve(date));
  });
}

/**
 * Agenda vazia para qualquer data. Serve aos testes que só precisam de um ADMIN
 * pousando no shell: sem ele a tela de agenda bate numa rota sem handler e o
 * MSW, configurado com onUnhandledRequest 'error', enche o log de ruído.
 */
export function anyDayEmpty() {
  return dayScheduleHandler((date) => day(date));
}

export function createExceptionOk(onRequest?: (body: ScheduleExceptionRequest) => void, id = 99) {
  return http.post('/api/schedule-exceptions', async ({ request }) => {
    const body = (await request.json()) as ScheduleExceptionRequest;
    onRequest?.(body);
    return HttpResponse.json({ id, ...body }, { status: 201 });
  });
}

export function createExceptionBadRequest(detail: string) {
  return http.post('/api/schedule-exceptions', () => problem(400, 'Bad Request', detail));
}

export function deleteExceptionOk(onRequest?: (id: string) => void) {
  return http.delete('/api/schedule-exceptions/:id', ({ params }) => {
    onRequest?.(String(params.id));
    return new HttpResponse(null, { status: 204 });
  });
}

export function problem(status: number, title: string, detail: string) {
  return HttpResponse.json(
    { type: 'about:blank', title, status, detail },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}
