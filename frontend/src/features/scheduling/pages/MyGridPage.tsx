import { useSearchParams } from 'react-router-dom';
import { ListFeedback } from '@/components/ListFeedback';
import { isIsoDate, todayIsoDate } from '@/lib/dates';
import { useMyGrid } from '../api/schedulingQueries';
import { DayNavigator } from '../components/DayNavigator';

/**
 * A grade do professor: o dia dele agrupado por hora, sem ação nenhuma.
 *
 * Cancelar e remarcar são do ADMIN por decisão de autorização (o professor é
 * dono da aula, não da agenda), então a tela não oferece o que a API recusaria.
 * O agrupamento já vem pronto do back — reagrupar aqui duplicaria a regra.
 */
export function MyGridPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const dateParam = searchParams.get('date');
  const date = isIsoDate(dateParam) ? dateParam : todayIsoDate();

  const grid = useMyGrid(date);
  const slots = grid.data?.slots ?? [];

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Minha grade</h1>

      <DayNavigator date={date} onChange={(next) => setSearchParams({ date: next })} />

      <ListFeedback
        state={grid}
        loading="Carregando a sua grade…"
        empty={grid.data && slots.length === 0 ? 'Nenhuma aula neste dia.' : undefined}
      />

      {slots.map((slot) => (
        <section
          key={slot.time}
          aria-label={`Aulas das ${slot.time}`}
          className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
        >
          <h2 className="mb-2 text-base font-semibold text-slate-900">{slot.time}</h2>
          <ul className="divide-y divide-slate-100">
            {slot.students.map((student) => (
              <li key={student.studentId} className="py-1 text-sm text-slate-900">
                {student.studentName}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </section>
  );
}
