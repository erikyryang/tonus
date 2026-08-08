import { ActionMenu, type MenuAction } from '@/components/ui/actionMenu';
import { formatDayMonth } from '@/lib/formatters';
import type { DayScheduleEntry } from '../api/schedulingApi';
import type { HourBlock } from '../lib/dayGroups';

export interface EntryActions {
  onCancel: (entry: DayScheduleEntry) => void;
  onReschedule: (entry: DayScheduleEntry) => void;
  onUndo: (exceptionId: number, studentName: string, originDate?: string) => void;
}

export function HourBlockSection({ block, actions }: { block: HourBlock; actions: EntryActions }) {
  return (
    <section
      aria-label={`Aulas das ${block.time}`}
      className="rounded-lg border border-border bg-card p-4 shadow-sm"
    >
      <h2 className="mb-3 text-base font-semibold text-foreground">{block.time}</h2>

      {/* auto-fill, e não um número fixo de colunas: com dois professores as
          colunas ficam estreitas e o menu cola no nome; com seis, cabem mais
          por linha em vez de empurrar tudo para baixo. O mínimo é 19rem porque
          abaixo disso o nome trunca quando a linha tem a badge de remarcação. */}
      <div className="grid gap-x-6 gap-y-4 grid-cols-[repeat(auto-fill,minmax(19rem,1fr))]">
        {block.groups.map((group) => (
          <div key={group.teacherId} role="group" aria-label={group.teacherName}>
            <h3 className="mb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">{group.teacherName}</h3>
            <ul className="divide-y divide-border">
              {group.entries.map((entry) => (
                // Aluno remarcado para um dia útil aparece duas vezes: às vezes
                // na MESMA hora do template dele. O id sozinho não é chave.
                <li key={`${entry.studentId}-${entry.exceptionId ?? 'template'}`} className="flex items-center gap-2 py-1">
                  <span className="min-w-0 flex-1 truncate text-sm text-foreground">{entry.studentName}</span>
                  {entry.originDate && (
                    <span className="shrink-0 rounded-full bg-warning-subtle px-2 py-0.5 text-xs text-warning-emphasis">
                      remarcada de {formatDayMonth(entry.originDate)}
                    </span>
                  )}
                  <ActionMenu label={labelFor(entry)} actions={menuFor(entry, actions)} />
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  );
}

/**
 * O nome do aluno não basta para identificar a linha.
 *
 * Um aluno remarcado para um dia útil aparece duas vezes nesse dia, e pode cair
 * na MESMA hora do horário padrão dele — visto na validação visual: dois "Diego
 * Alves" às 09:00, com o mesmo professor. Quem enxerga a tela distingue os dois
 * pela badge; quem usa leitor de tela ouviria "Ações de Diego Alves" duas vezes
 * e não teria como escolher.
 */
function labelFor(entry: DayScheduleEntry): string {
  const origin = entry.originDate ? `, remarcada de ${formatDayMonth(entry.originDate)}` : '';
  return `Ações de ${entry.studentName} às ${entry.time}${origin}`;
}

/**
 * Aula que só está aqui por causa de uma remarcação oferece apenas "Desfazer".
 *
 * Não é simplificação de tela, é o que a API comporta. A exceção que trouxe a
 * aula é indexada pela data de ORIGEM, e a expansão do dia adiciona a
 * remarcação a partir dela, sem olhar as exceções do dia de destino. Cancelar
 * aqui gravaria uma segunda exceção, dispararia o WhatsApp e não tiraria a aula
 * da tela — ela apareceria ao mesmo tempo no bloco de horário e em
 * "Cancelamentos". Remarcar de novo a faria aparecer nos dois dias.
 *
 * O caminho correto é em dois passos explícitos: desfazer aqui e, no dia de
 * origem, cancelar ou remarcar. Resolver isso em um passo é mudança de API —
 * fica registrado na SPEC-007, não escondido atrás de um item de menu que mente.
 */
function menuFor(entry: DayScheduleEntry, actions: EntryActions): MenuAction[] {
  if (entry.exceptionId != null) {
    const { exceptionId, studentName, originDate } = entry;
    return [{ label: 'Desfazer', onSelect: () => actions.onUndo(exceptionId, studentName, originDate) }];
  }
  return [
    { label: 'Cancelar', onSelect: () => actions.onCancel(entry) },
    { label: 'Remarcar', onSelect: () => actions.onReschedule(entry) },
  ];
}
