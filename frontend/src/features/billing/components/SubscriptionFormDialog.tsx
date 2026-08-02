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
import { parseMoney } from '@/lib/formatters';
import type { SubscriptionInput } from '../api/billingApi';

/**
 * Só aluno e valor: o dia de vencimento é o dia do mês em que o aluno se
 * cadastrou (SPEC-004), e o back nem aceita mais o campo. Oferecê-lo aqui
 * prometeria uma escolha que não existe.
 *
 * O limite espelha o back: valor mínimo de um centavo. O valor entra como texto
 * porque é assim que se digita dinheiro em pt-BR — "350,50" — e sai número, que
 * é o que a API espera.
 */
const schema = z.object({
  studentId: z.coerce.number().int().positive('Selecione o aluno.'),
  monthlyAmount: z
    .string()
    .min(1, 'Informe o valor mensal.')
    // Valida no texto e converte na submissão, não aqui: um transform no schema
    // faria o tipo de entrada e o de saída do formulário divergirem, e o
    // react-hook-form passa a não conseguir tipar o resolver.
    .refine((value) => {
      const amount = parseMoney(value);
      return Number.isFinite(amount) && amount >= 0.01;
    }, 'O valor precisa ser maior que zero.'),
});

type SubscriptionFormValues = z.infer<typeof schema>;

export function SubscriptionFormDialog({
  title,
  students,
  defaultValues,
  studentLocked = false,
  pending,
  error,
  onSubmit,
  onClose,
}: {
  title: string;
  /** Já filtrada pela página: assinatura de aluno inativo não geraria fatura útil. */
  students: Student[];
  defaultValues?: SubscriptionFormValues;
  /** Na edição o aluno não muda: assinatura não troca de dono, cria-se outra. */
  studentLocked?: boolean;
  pending: boolean;
  error: unknown;
  onSubmit: (values: SubscriptionInput) => void;
  onClose: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SubscriptionFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaultValues ?? { studentId: 0, monthlyAmount: '' },
  });

  return (
    <Dialog title={title} onClose={onClose} busy={pending}>
      <form
        onSubmit={handleSubmit((values) => onSubmit({ ...values, monthlyAmount: parseMoney(values.monthlyAmount) }))}
        noValidate
        className="space-y-4"
      >
        <div className="space-y-1">
          <Label htmlFor="studentId">Aluno</Label>
          <Select
            id="studentId"
            disabled={studentLocked}
            aria-invalid={errors.studentId ? true : undefined}
            {...register('studentId')}
          >
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
          <Label htmlFor="monthlyAmount">Valor mensal (R$)</Label>
          <Input
            id="monthlyAmount"
            inputMode="decimal"
            placeholder="350,00"
            aria-invalid={errors.monthlyAmount ? true : undefined}
            {...register('monthlyAmount')}
          />
          {errors.monthlyAmount && <p className="text-sm text-red-600">{errors.monthlyAmount.message}</p>}
        </div>

        {/* De onde vem o vencimento, já que não há campo para ele: sem essa
            frase, o ADMIN procuraria o campo que sumiu. O ajuste de fim de mês
            é do back e é silencioso — quem se cadastrou dia 31 descobriria só
            em fevereiro que a fatura venceu no dia 28. */}
        <p className="text-sm text-slate-600">
          O vencimento é o dia do cadastro do aluno, e não muda. Em mês que não tem esse dia, a fatura vence
          no último dia do mês.
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
