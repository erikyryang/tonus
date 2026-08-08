import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { apiMessage } from '@/lib/apiError';
import { formatMoney } from '@/lib/formatters';
import type { Plan, PlanEnrollment } from '../api/billingApi';

/**
 * A student's plans: enrolling is checking the box, with no save button —
 * each checkbox is an isolated decision, and the back has an endpoint for each one. A
 * "save" at the end would require computing the diff here and sending N calls
 * anyway — with the difference that a failure in the middle would leave the screen lying
 * about what was persisted.
 *
 * Ending is a separate action, per row, with confirmation (change 8): unlike
 * enrolling, it has an effect on future billing, and the checkbox alone never
 * asked "are you sure?". While active, the plan keeps its checkbox checked
 * and locked — the one that turns it off is the "End" button, never a click on the checkbox.
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
  /** Only the active ones: enrolling in a deactivated plan wouldn't generate billing. */
  plans: Plan[];
  enrollments: PlanEnrollment[] | undefined;
  /** Studio configuration; absent while the query hasn't responded. */
  discountPercent: number | undefined;
  pending: boolean;
  error: unknown;
  onToggle: (plan: Plan, enrollment: PlanEnrollment | undefined) => void;
  /** Asks for confirmation before ending — the one who actually deactivates it is the ADMIN, afterward. */
  onRequestDeactivate: (plan: Plan, enrollment: PlanEnrollment) => void;
  onClose: () => void;
}) {
  const byPlan = new Map((enrollments ?? []).map((enrollment) => [enrollment.planId, enrollment]));
  const activeCount = (enrollments ?? []).filter((enrollment) => enrollment.active).length;

  return (
    <Dialog title={`Planos de ${studentName}`} onClose={onClose} busy={pending}>
      <div className="space-y-4">
        {plans.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nenhum plano ativo no catálogo.</p>
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
                    className="h-4 w-4 rounded border-input"
                    checked={active}
                    // Active: only the "End" button turns it off. The checkbox here becomes an
                    // indicator, not a control — two paths to the same
                    // action, one of them without confirmation, would defeat the purpose.
                    disabled={pending || enrollments === undefined || active}
                    onChange={() => onToggle(plan, enrollment)}
                  />
                  <label htmlFor={`plan-${plan.id}`} className="text-sm text-foreground">
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
          * The configured discount appears as information, and the total is NOT
          * computed here: the invoice value is the back's rule (sum, discount and
          * rounding just once on the total), and recomputing it on the screen would create
          * a second truth that diverges at the first cent. When the projected value
          * is actually needed, the back is the one who should answer it.
          */}
        {activeCount > 1 && discountPercent !== undefined && discountPercent > 0 && (
          <p className="text-sm text-foreground">
            Com {activeCount} planos, a fatura sai com {discountPercent}% de desconto sobre a soma.
          </p>
        )}

        <p className="text-sm text-muted-foreground">
          A mudança vale para as próximas faturas. As já emitidas não mudam de valor.
        </p>

        {error != null && (
          <p role="alert" className="text-sm text-danger">
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
