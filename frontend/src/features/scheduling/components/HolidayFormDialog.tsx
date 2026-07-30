import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { apiMessage } from '@/lib/apiError';
import type { HolidayInput } from '../api/schedulingApi';

const schema = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Informe a data.'),
  description: z
    .string()
    .trim()
    .min(1, 'Informe a descrição.')
    .max(255, 'A descrição pode ter no máximo 255 caracteres.'),
});

type HolidayForm = z.infer<typeof schema>;

export function HolidayFormDialog({
  pending,
  error,
  onSubmit,
  onClose,
}: {
  pending: boolean;
  error: unknown;
  onSubmit: (values: HolidayInput) => void;
  onClose: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<HolidayForm>({
    resolver: zodResolver(schema),
    defaultValues: { date: '', description: '' },
  });

  return (
    <Dialog title="Novo feriado" onClose={onClose} busy={pending}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        <div className="space-y-1">
          <Label htmlFor="date">Data</Label>
          <Input id="date" type="date" aria-invalid={errors.date ? true : undefined} {...register('date')} />
          {errors.date && <p className="text-sm text-red-600">{errors.date.message}</p>}
        </div>

        <div className="space-y-1">
          <Label htmlFor="description">Descrição</Label>
          <Input
            id="description"
            aria-invalid={errors.description ? true : undefined}
            {...register('description')}
          />
          {errors.description && <p className="text-sm text-red-600">{errors.description.message}</p>}
        </div>

        {/* O efeito real, e não "tem certeza?": a descrição vai no texto da
            mensagem, e mensagem enviada não volta atrás. */}
        <p className="text-sm font-medium text-slate-900">
          Ao salvar, todos os alunos ativos serão notificados por WhatsApp de que não haverá aula.
        </p>

        {error != null && (
          <p role="alert" className="text-sm text-red-600">
            {apiMessage(error)}
          </p>
        )}

        <DialogActions>
          <Button variant="outline" onClick={onClose} disabled={pending}>
            Cancelar
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? 'Salvando…' : 'Salvar'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
