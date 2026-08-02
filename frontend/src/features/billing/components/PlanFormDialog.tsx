import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { apiMessage } from '@/lib/apiError';
import { parseMoney } from '@/lib/formatters';
import type { PlanInput } from '../api/billingApi';

/**
 * Nome e valor. O valor entra como texto porque é assim que se digita dinheiro
 * em pt-BR — "350,50" — e sai número, que é o que a API espera.
 */
const schema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Informe o nome do plano.')
    .max(255, 'O nome pode ter no máximo 255 caracteres.'),
  monthlyPrice: z
    .string()
    .min(1, 'Informe o valor mensal.')
    // Valida no texto e converte na submissão, mesma razão do formulário
    // anterior: um transform no schema faria o tipo de entrada divergir do de
    // saída e o react-hook-form deixaria de tipar o resolver.
    .refine((value) => {
      const amount = parseMoney(value);
      return Number.isFinite(amount) && amount >= 0.01;
    }, 'O valor precisa ser maior que zero.'),
});

type PlanFormValues = z.infer<typeof schema>;

export function PlanFormDialog({
  title,
  defaultValues,
  pending,
  error,
  onSubmit,
  onClose,
}: {
  title: string;
  defaultValues?: PlanFormValues;
  pending: boolean;
  error: unknown;
  onSubmit: (values: PlanInput) => void;
  onClose: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PlanFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaultValues ?? { name: '', monthlyPrice: '' },
  });

  return (
    <Dialog title={title} onClose={onClose} busy={pending}>
      <form
        onSubmit={handleSubmit((values) => onSubmit({ ...values, monthlyPrice: parseMoney(values.monthlyPrice) }))}
        noValidate
        className="space-y-4"
      >
        <div className="space-y-1">
          <Label htmlFor="name">Nome</Label>
          <Input id="name" aria-invalid={errors.name ? true : undefined} {...register('name')} />
          {errors.name && <p className="text-sm text-red-600">{errors.name.message}</p>}
        </div>

        <div className="space-y-1">
          <Label htmlFor="monthlyPrice">Valor mensal (R$)</Label>
          <Input
            id="monthlyPrice"
            inputMode="decimal"
            placeholder="350,00"
            aria-invalid={errors.monthlyPrice ? true : undefined}
            {...register('monthlyPrice')}
          />
          {errors.monthlyPrice && <p className="text-sm text-red-600">{errors.monthlyPrice.message}</p>}
        </div>

        {/* Mudar o preço não reescreve fatura emitida: o valor cobrado é cópia
            do que valia na competência (SPEC-012). */}
        <p className="text-sm text-slate-600">
          O novo valor vale para as próximas faturas. As já emitidas mantêm o valor cobrado.
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
