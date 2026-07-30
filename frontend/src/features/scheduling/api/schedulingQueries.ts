import { keepPreviousData, useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { isConflict } from '@/lib/apiError';
import {
  type DefaultScheduleInput,
  type HolidayInput,
  dayScheduleKey,
  defaultSchedulesKey,
  holidaysKey,
  myGridKey,
  myScheduleKey,
  schedulingApi,
} from './schedulingApi';

export function useDaySchedule(date: string) {
  return useQuery({
    queryKey: dayScheduleKey(date),
    queryFn: () => schedulingApi.day(date),
    /**
     * Trocar de dia mantém o dia anterior na tela até o novo chegar. Piscar
     * para branco a cada clique na seta é o que mais atrapalha num painel de
     * recepção; o indicador de "atualizando" evita que a tela minta enquanto
     * mostra o dia velho sob a data nova.
     */
    placeholderData: keepPreviousData,
  });
}

/**
 * Sem optimistic update: mutação → invalidate → refetch.
 *
 * Na escala do estúdio o refetch é instantâneo, e escrever o resultado à mão no
 * cache exigiria reimplementar no front a expansão da agenda (template −
 * feriado − exceções + remarcações) só para desfazê-la se a chamada falhasse.
 */
function useDayInvalidation() {
  const queryClient = useQueryClient();
  return (dates: string[]) =>
    Promise.all(
      [...new Set(dates)].map((date) => queryClient.invalidateQueries({ queryKey: dayScheduleKey(date) })),
    );
}

export function useCancelClass(date: string) {
  const invalidateDays = useDayInvalidation();
  return useMutation({
    mutationFn: (studentId: number) => schedulingApi.createException({ studentId, date, type: 'CANCELED' }),
    onSuccess: () => invalidateDays([date]),
  });
}

export interface RescheduleInput {
  studentId: number;
  newDate: string;
  newTime: string;
}

export function useRescheduleClass(date: string) {
  const invalidateDays = useDayInvalidation();
  return useMutation({
    mutationFn: ({ studentId, newDate, newTime }: RescheduleInput) =>
      schedulingApi.createException({ studentId, date, type: 'RESCHEDULED', newDate, newTime }),
    // A aula sai de um dia e entra em outro: as duas agendas mudaram.
    onSuccess: (_response, { newDate }) => invalidateDays([date, newDate]),
  });
}

export interface UndoInput {
  exceptionId: number;
  /** Dia de origem da remarcação, quando o desfazer acontece no destino. */
  otherDate?: string;
}

export function useUndoException(date: string) {
  const invalidateDays = useDayInvalidation();
  return useMutation({
    mutationFn: ({ exceptionId }: UndoInput) => schedulingApi.deleteException(exceptionId),
    onSuccess: (_response, { otherDate }) => invalidateDays(otherDate ? [date, otherDate] : [date]),
  });
}

/**
 * Configuração da agenda (horário padrão e feriado) muda a expansão de
 * qualquer dia: o feriado apaga o dia inteiro, e apagar um horário leva junto
 * as exceções do aluno. Invalidar só a própria listagem deixaria a agenda do
 * ADMIN mostrando aula que não vai acontecer.
 */
function useSchedulingInvalidation() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ['scheduling'] });
}

export function useDefaultSchedules(page: number) {
  return useQuery({
    queryKey: defaultSchedulesKey(page),
    queryFn: () => schedulingApi.defaultSchedules(page),
    placeholderData: keepPreviousData,
  });
}

/**
 * O 409 também invalida. Ele significa que o aluno já tem horário — outro
 * admin cadastrou, ou a lista na tela envelheceu — e deixá-la como está
 * mostraria uma agenda em que aquele horário não existe.
 */
export function useCreateDefaultSchedule() {
  const invalidate = useSchedulingInvalidation();
  return useMutation({
    mutationFn: (input: DefaultScheduleInput) => schedulingApi.createDefaultSchedule(input),
    onSuccess: invalidate,
    onError: (error) => {
      if (isConflict(error)) invalidate();
    },
  });
}

export interface UpdateDefaultScheduleInput {
  id: number;
  input: DefaultScheduleInput;
}

export function useUpdateDefaultSchedule() {
  const invalidate = useSchedulingInvalidation();
  return useMutation({
    mutationFn: ({ id, input }: UpdateDefaultScheduleInput) => schedulingApi.updateDefaultSchedule(id, input),
    onSuccess: invalidate,
  });
}

export function useDeleteDefaultSchedule() {
  const invalidate = useSchedulingInvalidation();
  return useMutation({
    mutationFn: (id: number) => schedulingApi.deleteDefaultSchedule(id),
    onSuccess: invalidate,
  });
}

export function useHolidays(page: number) {
  return useQuery({
    queryKey: holidaysKey(page),
    queryFn: () => schedulingApi.holidays(page),
    placeholderData: keepPreviousData,
  });
}

/** 409 aqui é "esse feriado já está cadastrado" — mesma leitura do horário. */
export function useCreateHoliday() {
  const invalidate = useSchedulingInvalidation();
  return useMutation({
    mutationFn: (input: HolidayInput) => schedulingApi.createHoliday(input),
    onSuccess: invalidate,
    onError: (error) => {
      if (isConflict(error)) invalidate();
    },
  });
}

export function useDeleteHoliday() {
  const invalidate = useSchedulingInvalidation();
  return useMutation({
    mutationFn: (id: number) => schedulingApi.deleteHoliday(id),
    onSuccess: invalidate,
  });
}

/**
 * A semana do aluno: um dia por chamada, porque /me/schedule responde por dia.
 *
 * O aluno precisa da semana e não de um dia só — a pergunta dele é "quando é a
 * minha próxima aula", e a resposta pode estar em qualquer dia até o próximo
 * horário. Cada dia vira uma entrada de cache própria, então voltar amanhã
 * reaproveita seis das sete respostas.
 */
export function useMyScheduleDays(dates: string[]) {
  return useQueries({
    queries: dates.map((date) => ({
      queryKey: myScheduleKey(date),
      queryFn: () => schedulingApi.mySchedule(date),
    })),
    combine: (results) => ({
      days: results.map((result) => result.data),
      isPending: results.some((result) => result.isPending),
      isFetching: results.some((result) => result.isFetching),
      isError: results.some((result) => result.isError),
      error: results.find((result) => result.error != null)?.error,
      refetch: () => results.forEach((result) => void result.refetch()),
    }),
  });
}

export function useMyGrid(date: string) {
  return useQuery({
    queryKey: myGridKey(date),
    queryFn: () => schedulingApi.myGrid(date),
    /** Trocar de dia mantém o dia anterior na tela até o novo chegar. */
    placeholderData: keepPreviousData,
  });
}
