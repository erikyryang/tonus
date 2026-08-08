import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { apiMessage, isConflict } from '@/lib/apiError';
import { todayIsoDate } from '@/lib/dates';
import { formatMoney } from '@/lib/formatters';
import type { Invoice } from '../api/billingApi';
import { studentLabel } from '../lib/studentLabel';

/**
 * A future date is blocked here and in the back. The duplication is the one allowed by
 * CLAUDE.md (form validation) and exists because this call isn't
 * harmless: accepting tomorrow would send the student a receipt for a payment that hasn't
 * happened yet and would push the subscription's due date forward.
 */
const schema = z.object({
  paymentDate: z
    .string()
    .min(1, 'Informe a data do pagamento.')
    .refine((value) => value <= todayIsoDate(), 'O pagamento não pode ter data futura.'),
});

type PaymentForm = z.infer<typeof schema>;

export function RegisterPaymentDialog({
  invoice,
  pending,
  error,
  onConfirm,
  onClose,
}: {
  invoice: Invoice;
  pending: boolean;
  error: unknown;
  onConfirm: (values: PaymentForm) => void;
  onClose: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PaymentForm>({
    resolver: zodResolver(schema),
    defaultValues: { paymentDate: todayIsoDate() },
  });

  /**
   * The 409 here means "another admin already settled it" — it's not transient, and the list
   * behind the dialog has already been redone. Retrying would only produce the same 409, so
   * the only sensible way out is Back.
   */
  const alreadySettled = isConflict(error);

  return (
    <Dialog title="Renovar fatura" onClose={onClose} busy={pending}>
      <form onSubmit={handleSubmit(onConfirm)} noValidate className="space-y-4">
        <p className="text-sm text-foreground">
          Fatura de <strong>{studentLabel(invoice)}</strong> — {formatMoney(invoice.amount)}.
        </p>

        <div className="space-y-1">
          <Label htmlFor="paymentDate">Data do pagamento</Label>
          <Input
            id="paymentDate"
            type="date"
            max={todayIsoDate()}
            aria-invalid={errors.paymentDate ? true : undefined}
            {...register('paymentDate')}
          />
          {errors.paymentDate && <p className="text-sm text-danger">{errors.paymentDate.message}</p>}
        </div>

        {/* The real effect, not "are you sure?": the WhatsApp message goes out and there's no way to unsend it.
            The due date doesn't come into play here anymore: payment doesn't move it (SPEC-004).
            A paid invoice is a renewed cycle — the text talks about renewal, not debt. */}
        <p className="text-sm font-medium text-foreground">
          O ciclo do aluno é renovado e ele receberá a confirmação por WhatsApp.
        </p>

        {error != null && (
          <p role="alert" className="text-sm text-danger">
            {apiMessage(error)}
          </p>
        )}

        <DialogActions>
          <Button variant="outline" onClick={onClose} disabled={pending}>
            Voltar
          </Button>
          <Button type="submit" disabled={pending || alreadySettled}>
            {pending ? 'Renovando…' : 'Renovar'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
