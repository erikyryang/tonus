import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ErrorNotice } from '@/components/ErrorNotice';
import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { apiMessage } from '@/lib/apiError';

const MAX_LENGTH = 500;

const schema = z.object({
  message: z
    .string()
    .trim()
    .min(1, 'Escreva a mensagem.')
    .max(MAX_LENGTH, `A mensagem pode ter no máximo ${MAX_LENGTH} caracteres.`),
});

type BroadcastForm = z.infer<typeof schema>;

/**
 * Mensagem livre para todo aluno ativo (mudança 9) — fora do ciclo de
 * comunicado de dia/efeito.
 *
 * "Enviar para N alunos ativos?" é buscado antes de confirmar, e não
 * calculado aqui: quem responde quantos alunos existem é o back
 * (GET /students?active=true), não um número que o front adivinha.
 */
export function BroadcastDialog({
  activeCount,
  countError,
  onRetryCount,
  pending,
  error,
  onSubmit,
  onClose,
}: {
  /** Ausente enquanto a contagem não responde — "ainda não sei" não vira "0". */
  activeCount: number | undefined;
  countError: unknown;
  onRetryCount: () => void;
  pending: boolean;
  error: unknown;
  onSubmit: (message: string) => void;
  onClose: () => void;
}) {
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<BroadcastForm>({
    resolver: zodResolver(schema),
    defaultValues: { message: '' },
  });

  const message = watch('message');
  const canSend = countError == null && activeCount !== undefined;

  return (
    <Dialog title="Enviar mensagem" onClose={onClose} busy={pending}>
      <form onSubmit={handleSubmit((values) => onSubmit(values.message))} noValidate className="space-y-4">
        <div className="space-y-1">
          <Label htmlFor="message">Mensagem</Label>
          <Textarea
            id="message"
            maxLength={MAX_LENGTH}
            aria-invalid={errors.message ? true : undefined}
            {...register('message')}
          />
          <p className="text-right text-xs text-slate-500">
            {message.length}/{MAX_LENGTH}
          </p>
          {errors.message && <p className="text-sm text-red-600">{errors.message.message}</p>}
        </div>

        {countError != null ? (
          <ErrorNotice
            message="Não foi possível saber quantos alunos ativos existem. Sem esse número, o envio fica bloqueado."
            onRetry={onRetryCount}
          />
        ) : (
          <p className="text-sm font-medium text-slate-900">
            {activeCount === undefined
              ? 'Contando os alunos ativos…'
              : `Enviar para ${activeCount} ${activeCount === 1 ? 'aluno ativo' : 'alunos ativos'}?`}
          </p>
        )}

        {error != null && (
          <p role="alert" className="text-sm text-red-600">
            {apiMessage(error)}
          </p>
        )}

        <DialogActions>
          <Button variant="outline" onClick={onClose} disabled={pending}>
            Cancelar
          </Button>
          <Button type="submit" disabled={pending || !canSend}>
            {pending ? 'Enviando…' : 'Enviar'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
