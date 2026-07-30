import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { apiMessage } from '@/lib/apiError';
import type { TeacherInput } from '../api/registrationApi';
import { personFields } from '../lib/personSchema';

const schema = z.object(personFields);

type TeacherForm = z.infer<typeof schema>;

export function TeacherFormDialog({
  title,
  defaultValues,
  pending,
  error,
  onSubmit,
  onClose,
}: {
  title: string;
  defaultValues?: TeacherInput;
  pending: boolean;
  error: unknown;
  onSubmit: (values: TeacherInput) => void;
  onClose: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<TeacherForm>({
    resolver: zodResolver(schema),
    defaultValues: defaultValues ?? { name: '', phone: '' },
  });

  return (
    <Dialog title={title} onClose={onClose} busy={pending}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        <div className="space-y-1">
          <Label htmlFor="name">Nome</Label>
          <Input id="name" aria-invalid={errors.name ? true : undefined} {...register('name')} />
          {errors.name && <p className="text-sm text-red-600">{errors.name.message}</p>}
        </div>

        <div className="space-y-1">
          <Label htmlFor="phone">Telefone</Label>
          <Input
            id="phone"
            inputMode="tel"
            placeholder="+5511999990000"
            aria-invalid={errors.phone ? true : undefined}
            {...register('phone')}
          />
          {errors.phone && <p className="text-sm text-red-600">{errors.phone.message}</p>}
        </div>

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
