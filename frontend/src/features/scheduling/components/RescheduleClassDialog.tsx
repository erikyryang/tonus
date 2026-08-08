import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { todayIsoDate } from '@/lib/dates';
import { formatDate } from '@/lib/formatters';
import { apiMessage } from '@/lib/apiError';

/**
 * A validação de data passada é duplicada de propósito — é a única duplicação
 * permitida pelo CLAUDE.md (validação de formulário). Ela evita a ida à API
 * para um erro de digitação óbvio; a autoridade continua sendo o back, e é a
 * mensagem dele que aparece quando o motivo é outro (feriado, por exemplo).
 */
const schema = z.object({
  newDate: z
    .string()
    .min(1, 'Informe a nova data.')
    .refine((value) => value >= todayIsoDate(), 'Não é possível remarcar para uma data passada.'),
  newTime: z.string().min(1, 'Informe a nova hora.'),
});

type RescheduleForm = z.infer<typeof schema>;

export function RescheduleClassDialog({
  studentName,
  date,
  currentTime,
  pending,
  error,
  onConfirm,
  onClose,
}: {
  studentName: string;
  date: string;
  /** Hora atual da aula: o caso comum é trocar o dia e manter o horário. */
  currentTime: string;
  pending: boolean;
  error: unknown;
  onConfirm: (values: RescheduleForm) => void;
  onClose: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RescheduleForm>({
    resolver: zodResolver(schema),
    defaultValues: { newDate: '', newTime: currentTime },
  });

  return (
    <Dialog title="Remarcar aula" onClose={onClose} busy={pending}>
      <form onSubmit={handleSubmit(onConfirm)} noValidate className="space-y-4">
        <p className="text-sm text-foreground">
          Aula de <strong>{studentName}</strong> em {formatDate(date)}.
        </p>

        <div className="space-y-1">
          <Label htmlFor="newDate">Nova data</Label>
          <Input
            id="newDate"
            type="date"
            min={todayIsoDate()}
            aria-invalid={errors.newDate ? true : undefined}
            {...register('newDate')}
          />
          {errors.newDate && <p className="text-sm text-danger">{errors.newDate.message}</p>}
        </div>

        <div className="space-y-1">
          <Label htmlFor="newTime">Nova hora</Label>
          <Input id="newTime" type="time" aria-invalid={errors.newTime ? true : undefined} {...register('newTime')} />
          {errors.newTime && <p className="text-sm text-danger">{errors.newTime.message}</p>}
        </div>

        <p className="text-sm text-muted-foreground">O aluno será avisado por WhatsApp.</p>

        {error != null && (
          <p role="alert" className="text-sm text-danger">
            {apiMessage(error)}
          </p>
        )}

        <DialogActions>
          <Button variant="outline" onClick={onClose} disabled={pending}>
            Voltar
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? 'Remarcando…' : 'Remarcar'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
