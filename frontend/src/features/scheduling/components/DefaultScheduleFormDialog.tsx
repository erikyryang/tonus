import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import type { Student } from '@/features/registration/api/registrationApi';
import { apiMessage } from '@/lib/apiError';
import type { DefaultScheduleInput } from '../api/schedulingApi';

const schema = z.object({
  studentId: z.coerce.number().int().positive('Selecione o aluno.'),
  time: z.string().regex(/^\d{2}:\d{2}$/, 'Informe a hora no formato HH:mm.'),
});

type ScheduleForm = z.infer<typeof schema>;

export function DefaultScheduleFormDialog({
  title,
  students,
  defaultValues,
  pending,
  error,
  onSubmit,
  onClose,
}: {
  title: string;
  /** Já filtrada pela página: horário de aluno inativo não teria efeito na agenda. */
  students: Student[];
  defaultValues?: DefaultScheduleInput;
  pending: boolean;
  error: unknown;
  onSubmit: (values: DefaultScheduleInput) => void;
  onClose: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ScheduleForm>({
    resolver: zodResolver(schema),
    defaultValues: defaultValues ?? { studentId: 0, time: '' },
  });

  return (
    <Dialog title={title} onClose={onClose} busy={pending}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        <div className="space-y-1">
          <Label htmlFor="studentId">Aluno</Label>
          <Select id="studentId" aria-invalid={errors.studentId ? true : undefined} {...register('studentId')}>
            <option value="">Selecione</option>
            {students.map((student) => (
              <option key={student.id} value={student.id}>
                {student.name}
              </option>
            ))}
          </Select>
          {errors.studentId && <p className="text-sm text-red-600">{errors.studentId.message}</p>}
        </div>

        <div className="space-y-1">
          <Label htmlFor="time">Hora</Label>
          <Input id="time" type="time" aria-invalid={errors.time ? true : undefined} {...register('time')} />
          {errors.time && <p className="text-sm text-red-600">{errors.time.message}</p>}
        </div>

        {/* O horário vale de segunda a sexta: a expansão da agenda não gera aula
            em fim de semana, e a tela não deve sugerir que gera. */}
        <p className="text-sm text-slate-600">A aula se repete de segunda a sexta, exceto em feriados.</p>

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
