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
 * Data futura é barrada aqui e no back. A duplicação é a permitida pelo
 * CLAUDE.md (validação de formulário) e existe porque esta chamada não é
 * inócua: aceitar amanhã mandaria ao aluno o recibo de um pagamento que ainda
 * não aconteceu e jogaria o vencimento da assinatura para frente.
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
    watch,
    formState: { errors },
  } = useForm<PaymentForm>({
    resolver: zodResolver(schema),
    defaultValues: { paymentDate: todayIsoDate() },
  });

  const newDueDay = dayOf(watch('paymentDate'));

  /**
   * O 409 daqui é "outro admin já deu a baixa" — não é transitório, e a lista
   * atrás do diálogo já foi refeita. Repetir só produziria o mesmo 409, então
   * a única saída que faz sentido é Voltar.
   */
  const alreadySettled = isConflict(error);

  return (
    <Dialog title="Dar baixa na fatura" onClose={onClose} busy={pending}>
      <form onSubmit={handleSubmit(onConfirm)} noValidate className="space-y-4">
        <p className="text-sm text-slate-700">
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
          {errors.paymentDate && <p className="text-sm text-red-600">{errors.paymentDate.message}</p>}
        </div>

        {/* O efeito real, não "tem certeza?": o WhatsApp sai e não há como desavisar. */}
        <p className="text-sm font-medium text-slate-900">
          O aluno receberá a confirmação por WhatsApp
          {newDueDay == null
            ? '.'
            : ` e o vencimento da assinatura será recalculado para o dia ${newDueDay}.`}
        </p>

        {error != null && (
          <p role="alert" className="text-sm text-red-600">
            {apiMessage(error)}
          </p>
        )}

        <DialogActions>
          <Button variant="outline" onClick={onClose} disabled={pending}>
            Voltar
          </Button>
          <Button type="submit" disabled={pending || alreadySettled}>
            {pending ? 'Dando baixa…' : 'Dar baixa'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** Dia do mês, sem zero à esquerda: "dia 5" é como se escreve, "dia 05" não. */
function dayOf(isoDate: string | undefined): number | null {
  const match = /^\d{4}-\d{2}-(\d{2})$/.exec(isoDate ?? '');
  return match ? Number(match[1]) : null;
}
