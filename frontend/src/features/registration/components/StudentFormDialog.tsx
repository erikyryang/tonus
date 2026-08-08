import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { apiMessage } from '@/lib/apiError';
import type { StudentInput, Teacher } from '../api/registrationApi';
import { personFields } from '../lib/personSchema';

const schema = z.object({
  ...personFields,
  // O select devolve string; 0 é o "selecione" ainda não respondido.
  teacherId: z.coerce.number().int().positive('Selecione o professor.'),
});

type StudentForm = z.infer<typeof schema>;

export function StudentFormDialog({
  title,
  teachers,
  defaultValues,
  pending,
  error,
  onSubmit,
  onClose,
}: {
  title: string;
  /** Já filtrada pela página: o back recusa vínculo com professor inativo. */
  teachers: Teacher[];
  defaultValues?: StudentInput;
  pending: boolean;
  error: unknown;
  onSubmit: (values: StudentInput) => void;
  onClose: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<StudentForm>({
    resolver: zodResolver(schema),
    defaultValues: defaultValues ?? { name: '', phone: '', teacherId: 0 },
  });

  return (
    <Dialog title={title} onClose={onClose} busy={pending}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        <div className="space-y-1">
          <Label htmlFor="name">Nome</Label>
          <Input id="name" aria-invalid={errors.name ? true : undefined} {...register('name')} />
          {errors.name && <p className="text-sm text-danger">{errors.name.message}</p>}
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
          {errors.phone && <p className="text-sm text-danger">{errors.phone.message}</p>}
        </div>

        <div className="space-y-1">
          <Label htmlFor="teacherId">Professor</Label>
          <Select id="teacherId" aria-invalid={errors.teacherId ? true : undefined} {...register('teacherId')}>
            <option value="">Selecione</option>
            {teachers.map((teacher) => (
              <option key={teacher.id} value={teacher.id}>
                {teacher.name}
              </option>
            ))}
          </Select>
          {errors.teacherId && <p className="text-sm text-danger">{errors.teacherId.message}</p>}
        </div>

        {error != null && (
          <p role="alert" className="text-sm text-danger">
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
