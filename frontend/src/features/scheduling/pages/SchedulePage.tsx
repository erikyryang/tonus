import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { isIsoDate, isWeekend, todayIsoDate } from '@/lib/dates';
import type { DaySchedule } from '../api/schedulingApi';
import { useCancelClass, useDaySchedule, useRescheduleClass, useUndoException } from '../api/schedulingQueries';
import { CancelClassDialog } from '../components/CancelClassDialog';
import { CancellationsSection } from '../components/CancellationsSection';
import { DayNavigator } from '../components/DayNavigator';
import { type EntryActions, HourBlockSection } from '../components/HourBlockSection';
import { RescheduleClassDialog } from '../components/RescheduleClassDialog';
import { UndoExceptionDialog } from '../components/UndoExceptionDialog';
import { apiMessage } from '@/lib/apiError';
import { groupByHour } from '../lib/dayGroups';

type PendingAction =
  | { kind: 'cancel'; studentId: number; studentName: string }
  | { kind: 'reschedule'; studentId: number; studentName: string; currentTime: string }
  | { kind: 'undo'; exceptionId: number; studentName: string; otherDate?: string };

/**
 * A tela central do estúdio. A data mora na URL (?date=) e não em useState:
 * é o que faz recarregar, voltar no histórico e mandar o link de um dia
 * específico continuarem funcionando — num painel de recepção o "volta pro dia
 * de ontem" é gesto de navegador, não de aplicação.
 */
export function SchedulePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const dateParam = searchParams.get('date');
  const date = isIsoDate(dateParam) ? dateParam : todayIsoDate();

  const [pending, setPending] = useState<PendingAction | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const day = useDaySchedule(date);
  const cancelClass = useCancelClass(date);
  const rescheduleClass = useRescheduleClass(date);
  const undoException = useUndoException(date);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);

  function closeDialog() {
    setPending(null);
    cancelClass.reset();
    rescheduleClass.reset();
    undoException.reset();
  }

  function succeed(message: string) {
    setToast(message);
    closeDialog();
  }

  const entryActions: EntryActions = {
    onCancel: (entry) => setPending({ kind: 'cancel', studentId: entry.studentId, studentName: entry.studentName }),
    onReschedule: (entry) =>
      setPending({
        kind: 'reschedule',
        studentId: entry.studentId,
        studentName: entry.studentName,
        currentTime: entry.time,
      }),
    onUndo: (exceptionId, studentName, originDate) =>
      setPending({ kind: 'undo', exceptionId, studentName, otherDate: originDate }),
  };

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Agenda</h1>

      <DayNavigator date={date} onChange={(next) => setSearchParams({ date: next })} />

      {/* Live region montada sempre: criada junto com a mensagem, o leitor de
          tela não anuncia — ele observa mudanças em regiões que já existem. */}
      <div role="status" aria-live="polite">
        {toast && (
          <span className="inline-block rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{toast}</span>
        )}
      </div>

      {day.isPending && <p className="text-sm text-slate-500">Carregando a agenda…</p>}

      {!day.isPending && day.isFetching && <p className="text-sm text-slate-400">Atualizando…</p>}

      {day.isError && (
        <div className="space-y-2">
          <p role="alert" className="text-sm text-red-600">
            {apiMessage(day.error)}
          </p>
          <Button variant="outline" size="sm" onClick={() => void day.refetch()}>
            Tentar de novo
          </Button>
        </div>
      )}

      {day.data && <DayBody date={date} day={day.data} actions={entryActions} onUndo={entryActions.onUndo} />}

      {pending?.kind === 'cancel' && (
        <CancelClassDialog
          studentName={pending.studentName}
          date={date}
          pending={cancelClass.isPending}
          error={cancelClass.error}
          onClose={closeDialog}
          onConfirm={() =>
            cancelClass.mutate(pending.studentId, { onSuccess: () => succeed('Aula cancelada. O aluno foi avisado.') })
          }
        />
      )}

      {pending?.kind === 'reschedule' && (
        <RescheduleClassDialog
          studentName={pending.studentName}
          date={date}
          currentTime={pending.currentTime}
          pending={rescheduleClass.isPending}
          error={rescheduleClass.error}
          onClose={closeDialog}
          onConfirm={(values) =>
            rescheduleClass.mutate(
              { studentId: pending.studentId, ...values },
              { onSuccess: () => succeed('Aula remarcada. O aluno foi avisado.') },
            )
          }
        />
      )}

      {pending?.kind === 'undo' && (
        <UndoExceptionDialog
          studentName={pending.studentName}
          pending={undoException.isPending}
          error={undoException.error}
          onClose={closeDialog}
          onConfirm={() =>
            undoException.mutate(
              { exceptionId: pending.exceptionId, otherDate: pending.otherDate },
              { onSuccess: () => succeed('Exceção desfeita.') },
            )
          }
        />
      )}
    </section>
  );
}

/**
 * Os estados de dia inteiro são mutuamente exclusivos e nesta ordem. O aviso de
 * fim de semana só vale para sábado/domingo *sem nada*: remarcação pontual para
 * o fim de semana é permitida pelo domínio, e escondê-la atrás do aviso faria a
 * tela mentir justamente no caso excepcional.
 */
function DayBody({
  date,
  day,
  actions,
  onUndo,
}: {
  date: string;
  day: DaySchedule;
  actions: EntryActions;
  onUndo: (exceptionId: number, studentName: string) => void;
}) {
  /*
   * O comunicado que suspende explica o dia vazio e substitui a agenda; o que
   * só informa aparece acima dela, porque o dia continua tendo aula.
   */
  if (day.announcement?.suspendsClasses) {
    return (
      <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
        Aulas suspensas: {day.announcement.description}.
      </p>
    );
  }

  const notice = day.announcement ? (
    <p className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
      Comunicado: {day.announcement.description}.
    </p>
  ) : null;

  const blocks = groupByHour(day.entries);

  if (blocks.length === 0 && day.cancellations.length === 0) {
    return (
      <div className="space-y-4">
        {notice}
        <p className="text-sm text-slate-500">
          {isWeekend(date) ? 'Sem aulas aos fins de semana.' : 'Nenhuma aula neste dia.'}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {notice}
      {blocks.map((block) => (
        <HourBlockSection key={block.time} block={block} actions={actions} />
      ))}
      {day.cancellations.length > 0 && <CancellationsSection cancellations={day.cancellations} onUndo={onUndo} />}
    </div>
  );
}
