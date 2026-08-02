import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { apiMessage } from '@/lib/apiError';
import { formatMoney } from '@/lib/formatters';
import type { Plan, PlanEnrollment } from '../api/billingApi';

/**
 * Os planos de um aluno, marcados e desmarcados na hora.
 *
 * Sem botão de salvar: cada caixa é uma decisão isolada, e o back tem endpoint
 * para cada uma. Um "salvar" no fim exigiria calcular o diff aqui e mandar N
 * chamadas mesmo assim — com a diferença de que uma falha no meio deixaria a
 * tela mentindo sobre o que foi gravado.
 *
 * Desmarcar desativa a matrícula, não a apaga: a linha guarda que aquele aluno
 * já teve aquele plano, e é o que permite rematricular sem esbarrar na UNIQUE.
 */
export function StudentPlansDialog({
  studentName,
  plans,
  enrollments,
  discountPercent,
  pending,
  error,
  onToggle,
  onClose,
}: {
  studentName: string;
  /** Só os ativos: matricular em plano desativado não geraria cobrança. */
  plans: Plan[];
  enrollments: PlanEnrollment[] | undefined;
  /** Configuração do estúdio; ausente enquanto a consulta não responde. */
  discountPercent: number | undefined;
  pending: boolean;
  error: unknown;
  onToggle: (plan: Plan, enrollment: PlanEnrollment | undefined) => void;
  onClose: () => void;
}) {
  const byPlan = new Map((enrollments ?? []).map((enrollment) => [enrollment.planId, enrollment]));
  const activeCount = (enrollments ?? []).filter((enrollment) => enrollment.active).length;

  return (
    <Dialog title={`Planos de ${studentName}`} onClose={onClose} busy={pending}>
      <div className="space-y-4">
        {plans.length === 0 ? (
          <p className="text-sm text-slate-500">Nenhum plano ativo no catálogo.</p>
        ) : (
          <ul className="space-y-2">
            {plans.map((plan) => {
              const enrollment = byPlan.get(plan.id);
              return (
                <li key={plan.id} className="flex items-center gap-2">
                  <input
                    id={`plan-${plan.id}`}
                    type="checkbox"
                    className="h-4 w-4 rounded border-slate-300"
                    checked={enrollment?.active ?? false}
                    disabled={pending || enrollments === undefined}
                    onChange={() => onToggle(plan, enrollment)}
                  />
                  <label htmlFor={`plan-${plan.id}`} className="text-sm text-slate-900">
                    {plan.name} — {formatMoney(plan.monthlyPrice)}
                  </label>
                </li>
              );
            })}
          </ul>
        )}

        {/*
          * O desconto configurado aparece como informação, e o total NÃO é
          * calculado aqui: o valor da fatura é regra do back (soma, desconto e
          * arredondamento uma vez só no total), e recomputá-lo na tela criaria
          * uma segunda verdade que diverge no primeiro centavo. Quando o valor
          * previsto for necessário de fato, quem deve respondê-lo é o back.
          */}
        {activeCount > 1 && discountPercent !== undefined && discountPercent > 0 && (
          <p className="text-sm text-slate-700">
            Com {activeCount} planos, a fatura sai com {discountPercent}% de desconto sobre a soma.
          </p>
        )}

        <p className="text-sm text-slate-600">
          A mudança vale para as próximas faturas. As já emitidas não mudam de valor.
        </p>

        {error != null && (
          <p role="alert" className="text-sm text-red-600">
            {apiMessage(error)}
          </p>
        )}

        <DialogActions>
          <Button variant="outline" onClick={onClose} disabled={pending}>
            Fechar
          </Button>
        </DialogActions>
      </div>
    </Dialog>
  );
}
