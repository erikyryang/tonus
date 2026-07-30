import { http, HttpResponse } from 'msw';
import type {
  DaySchedule,
  DayScheduleCancellation,
  DayScheduleEntry,
  DefaultSchedule,
  DefaultScheduleInput,
  Holiday,
  HolidayInput,
  ScheduleExceptionRequest,
  TeacherGrid,
  TeacherGridSlot,
} from '@/features/scheduling/api/schedulingApi';
import type { Page } from '@/lib/page';
import { pageOf } from './pageOf';
import { problem } from './problem';

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

export { problem };

export function defaultSchedule(overrides: Partial<DefaultSchedule> & { id: number }): DefaultSchedule {
  return { studentId: 100 + overrides.id, time: '07:00', ...overrides };
}

export function holiday(overrides: Partial<Holiday> & { id: number }): Holiday {
  return { date: HOLIDAY, description: 'Independência', ...overrides };
}

export function defaultSchedulesHandler(
  resolve: (params: URLSearchParams) => Page<DefaultSchedule>,
  log?: string[],
) {
  return http.get('/api/default-schedules', ({ request }) => {
    const url = new URL(request.url);
    log?.push(url.search);
    return HttpResponse.json(resolve(url.searchParams));
  });
}

export function defaultSchedulesList(...schedules: DefaultSchedule[]) {
  return defaultSchedulesHandler(() => pageOf(schedules));
}

export function createDefaultScheduleOk(onRequest?: (body: DefaultScheduleInput) => void, id = 90) {
  return http.post('/api/default-schedules', async ({ request }) => {
    const body = (await request.json()) as DefaultScheduleInput;
    onRequest?.(body);
    return HttpResponse.json(defaultSchedule({ id, ...body }), { status: 201 });
  });
}

export function updateDefaultScheduleOk(onRequest?: (id: number, body: DefaultScheduleInput) => void) {
  return http.put('/api/default-schedules/:id', async ({ params, request }) => {
    const body = (await request.json()) as DefaultScheduleInput;
    const id = Number(params.id);
    onRequest?.(id, body);
    return HttpResponse.json(defaultSchedule({ id, ...body }));
  });
}

export function deleteDefaultScheduleOk(onRequest?: (id: number) => void) {
  return http.delete('/api/default-schedules/:id', ({ params }) => {
    onRequest?.(Number(params.id));
    return new HttpResponse(null, { status: 204 });
  });
}

export function holidaysHandler(resolve: (params: URLSearchParams) => Page<Holiday>, log?: string[]) {
  return http.get('/api/holidays', ({ request }) => {
    const url = new URL(request.url);
    log?.push(url.search);
    return HttpResponse.json(resolve(url.searchParams));
  });
}

export function holidaysList(...holidays: Holiday[]) {
  return holidaysHandler(() => pageOf(holidays));
}

export function createHolidayOk(onRequest?: (body: HolidayInput) => void, id = 80) {
  return http.post('/api/holidays', async ({ request }) => {
    const body = (await request.json()) as HolidayInput;
    onRequest?.(body);
    return HttpResponse.json(holiday({ id, ...body }), { status: 201 });
  });
}

export function deleteHolidayOk(onRequest?: (id: number) => void) {
  return http.delete('/api/holidays/:id', ({ params }) => {
    onRequest?.(Number(params.id));
    return new HttpResponse(null, { status: 204 });
  });
}

/** GET /me/schedule — a agenda do aluno logado; o id sai do token, nunca da URL. */
export function myScheduleHandler(resolve: (date: string) => DaySchedule, log?: string[]) {
  return http.get('/api/me/schedule', ({ request }) => {
    const date = new URL(request.url).searchParams.get('date') ?? '';
    log?.push(date);
    return HttpResponse.json(resolve(date));
  });
}

export function anyMyScheduleEmpty() {
  return myScheduleHandler((date) => day(date));
}

export function grid(date: string, slots: TeacherGridSlot[] = []): TeacherGrid {
  return { date, slots };
}

/** GET /me/grid — a grade do professor logado, já agrupada por hora pelo back. */
export function myGridHandler(resolve: (date: string) => TeacherGrid, log?: string[]) {
  return http.get('/api/me/grid', ({ request }) => {
    const date = new URL(request.url).searchParams.get('date') ?? '';
    log?.push(date);
    return HttpResponse.json(resolve(date));
  });
}

export function anyMyGridEmpty() {
  return myGridHandler((date) => grid(date));
}
