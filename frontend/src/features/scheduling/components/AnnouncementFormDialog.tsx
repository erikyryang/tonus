import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { apiMessage } from '@/lib/apiError';
import type { AnnouncementInput } from '../api/schedulingApi';

const schema = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Informe a data.'),
  description: z
    .string()
    .trim()
    .min(1, 'Informe a descrição.')
    .max(255, 'A descrição pode ter no máximo 255 caracteres.'),
  suspendsClasses: z.boolean(),
});

type AnnouncementForm = z.infer<typeof schema>;

export function AnnouncementFormDialog({
  pending,
  error,
  onSubmit,
  onClose,
}: {
  pending: boolean;
  error: unknown;
  onSubmit: (values: AnnouncementInput) => void;
  onClose: () => void;
}) {
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<AnnouncementForm>({
    resolver: zodResolver(schema),
    // Desmarcado por padrão: suprimir o dia é o efeito caro, e efeito caro não
    // acontece por omissão.
    defaultValues: { date: '', description: '', suspendsClasses: false },
  });

  const suspends = watch('suspendsClasses');

  return (
    <Dialog title="Novo comunicado" onClose={onClose} busy={pending}>
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

        <div className="flex items-center gap-2">
          <input
            id="suspendsClasses"
            type="checkbox"
            className="h-4 w-4 rounded border-slate-300"
            {...register('suspendsClasses')}
          />
          <Label htmlFor="suspendsClasses">Suspende as aulas do dia</Label>
        </div>

        {/* O efeito real, e não "tem certeza?": a descrição vai no texto da
            mensagem, e mensagem enviada não volta atrás. O texto muda com a
            caixa porque as duas mensagens são opostas — quem avisa precisa
            saber qual delas o aluno vai ler. */}
        <p className="text-sm font-medium text-slate-900">
          Ao salvar, todos os alunos ativos serão notificados por WhatsApp
          {suspends
            ? ' de que não haverá aula nesse dia.'
            : '. A mensagem diz que as aulas acontecem normalmente.'}
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
