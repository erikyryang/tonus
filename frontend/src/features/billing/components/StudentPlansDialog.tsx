import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { apiMessage } from '@/lib/apiError';
import { formatMoney } from '@/lib/formatters';
import type { Plan, PlanEnrollment } from '../api/billingApi';

/**
 * Os planos de um aluno: matricular é marcar a caixa, sem botão de salvar —
 * cada caixa é uma decisão isolada, e o back tem endpoint para cada uma. Um
 * "salvar" no fim exigiria calcular o diff aqui e mandar N chamadas mesmo
 * assim — com a diferença de que uma falha no meio deixaria a tela mentindo
 * sobre o que foi gravado.
 *
 * Encerrar é ação à parte, por linha, com confirmação (mudança 8): diferente
 * de matricular, tem efeito sobre cobrança futura, e a caixa sozinha nunca
 * perguntava "tem certeza?". Enquanto ativo, o plano fica com a caixa marcada
 * e travada — quem desliga é o botão "Encerrar", nunca o clique na caixa.
 */
export function StudentPlansDialog({
  studentName,
  plans,
  enrollments,
  discountPercent,
  pending,
  error,
  onToggle,
  onRequestDeactivate,
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
  /** Pede confirmação antes de encerrar — quem desativa de fato é o ADMIN, depois. */
  onRequestDeactivate: (plan: Plan, enrollment: PlanEnrollment) => void;
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
              const active = enrollment?.active ?? false;
              return (
                <li key={plan.id} className="flex items-center gap-2">
                  <input
                    id={`plan-${plan.id}`}
                    type="checkbox"
                    className="h-4 w-4 rounded border-slate-300"
                    checked={active}
                    // Ativo: só o botão "Encerrar" desliga. A caixa aqui vira
                    // indicador, não controle — dois caminhos para a mesma
                    // ação, um deles sem confirmação, derrotaria o propósito.
                    disabled={pending || enrollments === undefined || active}
                    onChange={() => onToggle(plan, enrollment)}
                  />
                  <label htmlFor={`plan-${plan.id}`} className="text-sm text-slate-900">
                    {plan.name} — {formatMoney(plan.monthlyPrice)}
                  </label>
                  {active && enrollment && (
                    <Button
                      size="sm"
                      variant="outline"
                      className="ml-auto"
                      disabled={pending}
                      onClick={() => onRequestDeactivate(plan, enrollment)}
                    >
                      Encerrar
                    </Button>
                  )}
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
