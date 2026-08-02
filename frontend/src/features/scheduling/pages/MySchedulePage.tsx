import { useMemo } from 'react';
import { ListFeedback } from '@/components/ListFeedback';
import { formatWeekday, isWeekend, todayIsoDate } from '@/lib/dates';
import { formatDate, formatDayMonth } from '@/lib/formatters';
import type { DaySchedule } from '../api/schedulingApi';
import { useMyScheduleWeek } from '../api/schedulingQueries';

/**
 * Uma semana à frente, contando de hoje. É o recorte que responde à pergunta do
 * aluno ("quando é a minha próxima aula?") sem transformar a tela numa agenda
 * navegável: quem quer ver longe pergunta na recepção, e o WhatsApp avisa das
 * mudanças de qualquer jeito.
 */
const WINDOW_DAYS = 7;

export function MySchedulePage() {
  // Fixado na montagem: a janela não pode escorregar a cada render, senão a
  // chave da query muda sozinha e a tela volta ao servidor sem ninguém pedir.
  const from = useMemo(() => todayIsoDate(), []);

  const week = useMyScheduleWeek(from, WINDOW_DAYS);
  const days = week.data ?? [];
  /**
   * As conclusões sobre o conjunto ("você ainda não tem horário") só valem com
   * a janela inteira em mãos — meia semana faria delas um palpite. Com uma
   * chamada só, isso é simplesmente ter a resposta: ou ela chegou, ou o erro
   * aparece acima com o "tentar de novo".
   */
  const complete = week.data != null;

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
  const hasContent = hasSchedule || days.some((day) => day.announcement);
  /**
   * A ausência de aula só significa "não tem horário" se algum dia da janela
   * pudesse ter mostrado uma. Fim de semana nunca tem, e feriado suprime o dia
   * para todo mundo — numa semana de recesso o aluno com horário veria os sete
   * dias vazios e seria mandado à recepção resolver o que não está quebrado.
   */
  const couldHaveShownClass = days.some((day) => !day.announcement?.suspendsClasses && !isWeekend(day.date));

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
      {complete && couldHaveShownClass && !hasSchedule && (
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

/**
 * Aula e cancelamento convivem no mesmo dia: a aula de hoje pode ter sido
 * cancelada e a de amanhã remarcada para hoje. Mostrar só a aula, como fazia a
 * primeira versão, apagava justamente o aviso que o aluno precisa ver.
 */
function DayStatus({ day }: { day: DaySchedule }) {
  if (day.announcement?.suspendsClasses) {
    return (
      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">
        Sem aula — {day.announcement.description}
      </span>
    );
  }

  /*
   * O comunicado que não suspende acompanha a aula em vez de substituí-la: o
   * aluno recebeu o WhatsApp e volta aqui para conferir, e mostrar o aviso sem
   * a hora — ou a hora sem o aviso — deixaria metade da informação fora da
   * única tela que ele abre.
   */
  const notice = day.announcement ? (
    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700">
      {day.announcement.description}
    </span>
  ) : null;

  if (day.entries.length === 0 && day.cancellations.length === 0) {
    return (
      <>
        {notice}
        <span className="text-sm text-slate-500">{isWeekend(day.date) ? 'Fim de semana' : 'Sem aula'}</span>
      </>
    );
  }

  return (
    <>
      {notice}
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
      {day.cancellations.map((cancellation) => (
        <span
          key={cancellation.exceptionId}
          className="rounded-full bg-red-100 px-2 py-0.5 text-xs text-red-800"
        >
          {cancellation.time ? `Aula das ${cancellation.time} cancelada` : 'Aula cancelada'}
        </span>
      ))}
    </>
  );
}
