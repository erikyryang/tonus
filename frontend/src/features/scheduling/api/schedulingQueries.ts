import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { dayScheduleKey, schedulingApi } from './schedulingApi';

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
