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

/** O comunicado do dia, quando existe. */
export interface DayAnnouncement {
  description: string;
  suspendsClasses: boolean;
}

/** Espelha o DTO de GET /schedule/day. */
export interface DaySchedule {
  date: string;
  /**
   * Ausente no dia sem comunicado. Quando ele suspende as aulas, `entries` e
   * `cancellations` vêm vazios — o dia inteiro está suprimido.
   */
  announcement?: DayAnnouncement | null;
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

/**
 * Espelha o DTO de /announcements.
 *
 * `suspendsClasses` separa avisar de cancelar: com ele o dia some da agenda
 * (era o antigo feriado); sem ele o comunicado só informa e a agenda segue.
 */
export interface Announcement {
  id: number;
  date: string;
  description: string;
  suspendsClasses: boolean;
}

export interface AnnouncementInput {
  date: string;
  description: string;
  suspendsClasses: boolean;
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

export const schedulesByStudentKey = ['scheduling', 'schedules-by-student'] as const;

export const announcementsKey = (page?: number) =>
  page === undefined
    ? (['scheduling', 'announcements'] as const)
    : (['scheduling', 'announcements', page] as const);

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

  /**
   * A coluna "Horário" de /students (SPEC-009): mapa aluno → hora, para o
   * front cruzar com a tabela de alunos que já tem em mãos. O grafo não muda —
   * `registration` continua sem consultar `scheduling`.
   */
  schedulesByStudent: () => apiClient.get<Record<number, string>>('/default-schedules/by-student'),

  announcements: (page: number) => apiClient.get<Page<Announcement>>(`/announcements?page=${page}`),
  createAnnouncement: (input: AnnouncementInput) => apiClient.post<Announcement>('/announcements', input),
  deleteAnnouncement: (id: number) => apiClient.delete<void>(`/announcements/${id}`),

  /**
   * Disparo manual (mudança 9): mensagem livre para todo aluno ativo, fora do
   * ciclo de comunicado de dia/efeito. Não há listagem nem id para o front —
   * o back não guarda entidade própria, só o evento que ele publica.
   */
  broadcast: (message: string) => apiClient.post<void>('/announcements/broadcast', { message }),

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
