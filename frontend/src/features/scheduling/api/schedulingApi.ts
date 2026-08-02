import { apiClient } from '@/lib/apiClient';
import type { Page } from '@/lib/page';

/** De onde a aula veio: do horário recorrente ou de uma remarcação. */
export type ScheduleOrigin = 'TEMPLATE' | 'RESCHEDULED';

export type ExceptionType = 'CANCELED' | 'RESCHEDULED';

/**
 * Uma aula do dia. `exceptionId` e `originDate` só vêm quando a origem é
 * RESCHEDULED — é a exceção que trouxe a aula para cá, e é por ela que o ADMIN
 * desfaz a remarcação.
 */
export interface DayScheduleEntry {
  studentId: number;
  studentName: string;
  teacherId: number;
  teacherName: string;
  /** HH:mm — a agenda é marcada em minutos cheios. */
  time: string;
  origin: ScheduleOrigin;
  originDate?: string;
  exceptionId?: number;
}

/** Aula cancelada neste dia. Não some da tela: é o que o ADMIN precisa desfazer. */
export interface DayScheduleCancellation {
  exceptionId: number;
  studentId: number;
  studentName: string;
  teacherId: number;
  teacherName: string;
  /** Hora do horário padrão do aluno; ausente se ele não tiver mais um. */
  time?: string;
}

/** Espelha o DTO de GET /schedule/day. */
export interface DaySchedule {
  date: string;
  /** Descrição do feriado; ausente quando o dia não é feriado. */
  holiday?: string;
  entries: DayScheduleEntry[];
  cancellations: DayScheduleCancellation[];
}

export interface ScheduleExceptionRequest {
  studentId: number;
  date: string;
  type: ExceptionType;
  newDate?: string;
  newTime?: string;
}

export interface ScheduleExceptionResponse {
  id: number;
  studentId: number;
  date: string;
  type: ExceptionType;
  newDate?: string;
  newTime?: string;
}

/** Espelha o DTO de /default-schedules. `time` é HH:mm. */
export interface DefaultSchedule {
  id: number;
  studentId: number;
  time: string;
}

export interface DefaultScheduleInput {
  studentId: number;
  time: string;
}

/** Espelha o DTO de /holidays. */
export interface Holiday {
  id: number;
  date: string;
  description: string;
}

export interface HolidayInput {
  date: string;
  description: string;
}

/**
 * A grade do professor, já agrupada por hora pelo back (GET /me/grid). O grupo
 * emerge de professor + hora: não existe turma como entidade, então não há id
 * de turma para o front agrupar por — e reagrupar aqui duplicaria a regra.
 *
 * O telefone vem por finalidade: quem lê é o professor daquele aluno, e falar
 * com ele é o atendimento. Nenhum outro caminho da agenda carrega telefone.
 */
export interface TeacherGridStudent {
  studentId: number;
  studentName: string;
  /** E.164, como o cadastro guarda: +5511999990000. */
  studentPhone: string;
}

export interface TeacherGridSlot {
  time: string;
  students: TeacherGridStudent[];
}

export interface TeacherGrid {
  date: string;
  slots: TeacherGridSlot[];
}

export const dayScheduleKey = (date: string) => ['scheduling', 'day', date] as const;

export const defaultSchedulesKey = (page?: number) =>
  page === undefined
    ? (['scheduling', 'default-schedules'] as const)
    : (['scheduling', 'default-schedules', page] as const);

export const holidaysKey = (page?: number) =>
  page === undefined ? (['scheduling', 'holidays'] as const) : (['scheduling', 'holidays', page] as const);

export const myScheduleWeekKey = (from: string, days: number) =>
  ['scheduling', 'me', 'schedule', 'week', from, days] as const;
export const myGridKey = (date: string) => ['scheduling', 'me', 'grid', date] as const;

export const schedulingApi = {
  day: (date: string) => apiClient.get<DaySchedule>(`/schedule/day?date=${date}`),
  createException: (request: ScheduleExceptionRequest) =>
    apiClient.post<ScheduleExceptionResponse>('/schedule-exceptions', request),
  deleteException: (id: number) => apiClient.delete<void>(`/schedule-exceptions/${id}`),

  defaultSchedules: (page: number) => apiClient.get<Page<DefaultSchedule>>(`/default-schedules?page=${page}`),
  createDefaultSchedule: (input: DefaultScheduleInput) =>
    apiClient.post<DefaultSchedule>('/default-schedules', input),
  updateDefaultSchedule: (id: number, input: DefaultScheduleInput) =>
    apiClient.put<DefaultSchedule>(`/default-schedules/${id}`, input),
  /**
   * DELETE existe de verdade aqui, diferente de aluno e professor: horário
   * padrão é configuração corrente, não histórico.
   */
  deleteDefaultSchedule: (id: number) => apiClient.delete<void>(`/default-schedules/${id}`),

  holidays: (page: number) => apiClient.get<Page<Holiday>>(`/holidays?page=${page}`),
  createHoliday: (input: HolidayInput) => apiClient.post<Holiday>('/holidays', input),
  deleteHoliday: (id: number) => apiClient.delete<void>(`/holidays/${id}`),

  /**
   * O id do aluno e o do professor vêm do token: /me não aceita id nenhum.
   *
   * A janela vem numa chamada só. Era uma por dia, e cada uma custava ao back
   * a expansão da agenda do estúdio inteiro para devolver a linha de um aluno.
   */
  myScheduleWeek: (from: string, days: number) =>
    apiClient.get<DaySchedule[]>(`/me/schedule/week?from=${from}&days=${days}`),
  myGrid: (date: string) => apiClient.get<TeacherGrid>(`/me/grid?date=${date}`),
};
