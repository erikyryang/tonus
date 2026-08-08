import { ActionMenu } from '@/components/ui/actionMenu';
import type { DayScheduleCancellation } from '../api/schedulingApi';

/**
 * Cancelado não some da tela. O ADMIN precisa ver o que cancelou — é a única
 * forma de perceber o engano e desfazer.
 */
export function CancellationsSection({
  cancellations,
  onUndo,
}: {
  cancellations: DayScheduleCancellation[];
  onUndo: (exceptionId: number, studentName: string) => void;
}) {
  return (
    <section aria-label="Cancelamentos" className="rounded-lg border border-border bg-card p-4 shadow-sm">
      <h2 className="mb-3 text-base font-semibold text-foreground">Cancelamentos</h2>
      <ul className="divide-y divide-border">
        {cancellations.map((cancellation) => (
          <li key={cancellation.exceptionId} className="flex items-center gap-2 py-1">
            <span className="w-14 shrink-0 text-sm text-muted-foreground">{cancellation.time ?? '—'}</span>
            <span className="min-w-0 flex-1 truncate text-sm text-muted-foreground line-through">
              {cancellation.studentName}
            </span>
            <span className="shrink-0 text-xs text-muted-foreground">{cancellation.teacherName}</span>
            <ActionMenu
              // "cancelada" no rótulo: o mesmo aluno pode ter uma aula normal
              // no dia e ainda assim aparecer aqui por outra exceção.
              label={`Ações de ${cancellation.studentName}, aula cancelada`}
              actions={[
                {
                  label: 'Desfazer',
                  onSelect: () => onUndo(cancellation.exceptionId, cancellation.studentName),
                },
              ]}
            />
          </li>
        ))}
      </ul>
    </section>
  );
}
