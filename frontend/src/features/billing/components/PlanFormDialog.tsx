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
 * Name and value. The value comes in as text because that's how money is typed
 * in pt-BR — "350,50" — and goes out as a number, which is what the API expects.
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
    // Validates on the text and converts on submit, same reason as the previous
    // form: a transform on the schema would make the input type diverge from the
    // output type and react-hook-form would stop typing the resolver.
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

        {/* Changing the price doesn't rewrite an issued invoice: the charged value is a copy
            of what was in effect for that reference month (SPEC-012). */}
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
