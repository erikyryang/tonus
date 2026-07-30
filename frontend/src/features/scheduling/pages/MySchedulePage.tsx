import { useMemo } from 'react';
import { ListFeedback } from '@/components/ListFeedback';
import { addDays, formatWeekday, isWeekend, todayIsoDate } from '@/lib/dates';
import { formatDate, formatDayMonth } from '@/lib/formatters';
import type { DaySchedule } from '../api/schedulingApi';
import { useMyScheduleDays } from '../api/schedulingQueries';

/**
 * Uma semana à frente, contando de hoje. É o recorte que responde à pergunta do
 * aluno ("quando é a minha próxima aula?") sem transformar a tela numa agenda
 * navegável: quem quer ver longe pergunta na recepção, e o WhatsApp avisa das
 * mudanças de qualquer jeito.
 */
const WINDOW_DAYS = 7;

export function MySchedulePage() {
  const dates = useMemo(() => {
    const today = todayIsoDate();
    return Array.from({ length: WINDOW_DAYS }, (_, offset) => addDays(today, offset));
  }, []);

  const week = useMyScheduleDays(dates);
  const days = week.days.filter((day): day is DaySchedule => day != null);
  /**
   * Um dia da janela pode falhar sozinho — são sete chamadas independentes. Os
   * dias que chegaram continuam sendo mostrados (o erro aparece acima, com o
   * "tentar de novo"), mas as conclusões sobre o conjunto ficam suspensas: com
   * um buraco na semana, "você ainda não tem horário cadastrado" seria um
   * palpite, não uma informação.
   */
  const complete = days.length === dates.length && !week.isError;

  /**
   * O horário padrão não vem num campo próprio: /me/schedule devolve o dia já
   * expandido. As aulas de origem TEMPLATE são o horário recorrente — se todas
   * caem na mesma hora (o caso normal), é essa a hora do aluno.
   */
  const templateTimes = [
    ...new Set(days.flatMap((day) => day.entries.filter((entry) => entry.origin === 'TEMPLATE').map((entry) => entry.time))),
  ];
  /**
   * Ter horário e ter o que mostrar são coisas diferentes. Um feriado não conta
   * como horário — ele suprime o dia para todo mundo, inclusive para quem não
   * tem aula nenhuma —, mas conta como conteúdo: é a explicação de por que
   * aquele dia está vazio, e some junto com a lista se ela não aparecer.
   */
  const hasSchedule = days.some((day) => day.entries.length > 0 || day.cancellations.length > 0);
  const hasContent = hasSchedule || days.some((day) => day.holiday);

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Minha agenda</h1>

      <ListFeedback state={week} loading="Carregando a sua agenda…" />

      {complete && templateTimes.length === 1 && (
        <p className="text-sm text-slate-700">
          Seu horário: <strong>{templateTimes[0]}</strong>, de segunda a sexta.
        </p>
      )}

      {/* Sem horário e sem exceção: dizer "sem aula" sete vezes esconderia que
          o cadastro do horário é o que está faltando. */}
      {complete && !hasSchedule && (
        <p className="text-sm text-slate-500">
          Você ainda não tem horário cadastrado. Fale com a recepção para marcar o seu.
        </p>
      )}

      {hasContent && (
        <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white">
          {days.map((day) => (
            <DayRow key={day.date} day={day} />
          ))}
        </ul>
      )}
    </section>
  );
}

function DayRow({ day }: { day: DaySchedule }) {
  return (
    <li
      // O rótulo é o que dá nome à linha para quem usa leitor de tela: sem ele
      // o item seria só uma sequência de horas soltas.
      aria-label={`${formatWeekday(day.date)}, ${formatDate(day.date)}`}
      className="flex flex-wrap items-center justify-between gap-2 px-4 py-3"
    >
      <span className="text-sm text-slate-700">
        {formatWeekday(day.date)}, {formatDate(day.date)}
      </span>
      <span className="flex flex-wrap items-center gap-2">
        <DayStatus day={day} />
      </span>
    </li>
  );
}

function DayStatus({ day }: { day: DaySchedule }) {
  if (day.holiday) {
    return (
      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">Feriado — {day.holiday}</span>
    );
  }

  if (day.entries.length > 0) {
    return (
      <>
        {day.entries.map((entry) => (
          <span key={`${entry.time}-${entry.exceptionId ?? 'template'}`} className="flex items-center gap-2">
            <span className="text-sm font-semibold text-slate-900">{entry.time}</span>
            {entry.originDate && (
              <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">
                remarcada de {formatDayMonth(entry.originDate)}
              </span>
            )}
          </span>
        ))}
      </>
    );
  }

  if (day.cancellations.length > 0) {
    return <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs text-red-800">Aula cancelada</span>;
  }

  return <span className="text-sm text-slate-500">{isWeekend(day.date) ? 'Fim de semana' : 'Sem aula'}</span>;
}
