import { http, HttpResponse } from 'msw';
import type {
  DaySchedule,
  DayScheduleCancellation,
  DayScheduleEntry,
  DefaultSchedule,
  DefaultScheduleInput,
  Announcement,
  AnnouncementInput,
  ScheduleExceptionRequest,
  TeacherGrid,
  TeacherGridSlot,
} from '@/features/scheduling/api/schedulingApi';
import { addDays, todayIsoDate } from '@/lib/dates';
import type { Page } from '@/lib/page';
import { pageOf } from './pageOf';
import { problem } from './problem';

/**
 * As datas da agenda em teste andam com o calendário: ancoradas na segunda-feira
 * da próxima semana, nunca em literais.
 *
 * Literal apodrece. `RescheduleClassDialog` recusa data passada (a mesma regra
 * do back, duplicada no formulário), então quando 2026-07-30 virou ontem a
 * remarcação passou a ser barrada antes da rede e o teste de invalidação
 * quebrou sozinho — longe de qualquer mudança de código, que é o pior tipo de
 * quebra para investigar. Mesma lição do Clock fixo no back.
 *
 * O dia da semana continua fixo, e é por isso que a âncora é uma segunda: a
 * expansão da agenda só gera aula de segunda a sexta, e SATURDAY precisa cair
 * mesmo no fim de semana.
 */
function nextMonday(): string {
  const today = todayIsoDate();
  // 0 domingo … 6 sábado. O sufixo UTC explícito evita a leitura como local.
  const weekday = new Date(`${today}T00:00:00Z`).getUTCDay();
  return addDays(today, (8 - weekday) % 7 || 7);
}

const ANCHOR_MONDAY = nextMonday();

export const MONDAY = ANCHOR_MONDAY;
export const WEDNESDAY = addDays(ANCHOR_MONDAY, 2);
export const THURSDAY = addDays(ANCHOR_MONDAY, 3);
export const SATURDAY = addDays(ANCHOR_MONDAY, 5);
export const ANNOUNCED_DAY = addDays(ANCHOR_MONDAY, 30);

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

export function announcement(overrides: Partial<Announcement> & { id: number }): Announcement {
  return { date: ANNOUNCED_DAY, description: 'Independência', suspendsClasses: true, ...overrides };
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

/**
 * GET /default-schedules/by-student — a coluna "Horário" de /students
 * (SPEC-009). `registration` não passa a consultar `scheduling`: é a tela que
 * cruza esta lista com a tabela de alunos que já tem em mãos, mesmo padrão da
 * coluna de cobrança.
 */
export function schedulesByStudentHandler(resolve: () => Record<number, string>, log?: string[]) {
  return http.get('/api/default-schedules/by-student', () => {
    log?.push('by-student');
    return HttpResponse.json(resolve());
  });
}

export function schedulesByStudent(times: Record<number, string> = {}) {
  return schedulesByStudentHandler(() => times);
}

export function announcementsHandler(
  resolve: (params: URLSearchParams) => Page<Announcement>,
  log?: string[],
) {
  return http.get('/api/announcements', ({ request }) => {
    const url = new URL(request.url);
    log?.push(url.search);
    return HttpResponse.json(resolve(url.searchParams));
  });
}

export function announcementsList(...announcements: Announcement[]) {
  return announcementsHandler(() => pageOf(announcements));
}

export function createAnnouncementOk(onRequest?: (body: AnnouncementInput) => void, id = 80) {
  return http.post('/api/announcements', async ({ request }) => {
    const body = (await request.json()) as AnnouncementInput;
    onRequest?.(body);
    return HttpResponse.json(announcement({ id, ...body }), { status: 201 });
  });
}

export function deleteAnnouncementOk(onRequest?: (id: number) => void) {
  return http.delete('/api/announcements/:id', ({ params }) => {
    onRequest?.(Number(params.id));
    return new HttpResponse(null, { status: 204 });
  });
}

/** POST /announcements/broadcast — mensagem livre, sem entidade nem id de resposta. */
export function broadcastOk(onRequest?: (message: string) => void) {
  return http.post('/api/announcements/broadcast', async ({ request }) => {
    const body = (await request.json()) as { message: string };
    onRequest?.(body.message);
    return new HttpResponse(null, { status: 202 });
  });
}

/**
 * GET /me/schedule/week — a janela do aluno logado; o id sai do token, nunca
 * da URL.
 *
 * O resolver continua sendo por dia, e não pela janela inteira, porque é assim
 * que o teste pensa ("neste dia acontece isto"). Quem monta a lista a partir de
 * `from` e `days` é o handler, como faz o back.
 */
export function myScheduleHandler(resolve: (date: string) => DaySchedule, log?: string[]) {
  return http.get('/api/me/schedule/week', ({ request }) => {
    const params = new URL(request.url).searchParams;
    const from = params.get('from') ?? '';
    const days = Number(params.get('days') ?? 7);
    log?.push(`${from}:${days}`);
    return HttpResponse.json(Array.from({ length: days }, (_, offset) => resolve(addDays(from, offset))));
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
