import { apiClient } from '@/lib/apiClient';

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

export const dayScheduleKey = (date: string) => ['scheduling', 'day', date] as const;

export const schedulingApi = {
  day: (date: string) => apiClient.get<DaySchedule>(`/schedule/day?date=${date}`),
  createException: (request: ScheduleExceptionRequest) =>
    apiClient.post<ScheduleExceptionResponse>('/schedule-exceptions', request),
  deleteException: (id: number) => apiClient.delete<void>(`/schedule-exceptions/${id}`),
};
