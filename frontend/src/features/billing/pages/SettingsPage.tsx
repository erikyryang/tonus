import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ListFeedback } from '@/components/ListFeedback';
import { Toast, useToast } from '@/components/Toast';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { apiMessage } from '@/lib/apiError';
import { useBillingSettings, useUpdateBillingSettings } from '../api/billingQueries';

/** Espelha o CHECK do banco: 0 a 100. */
const schema = z.object({
  multiPlanDiscountPercent: z
    .string()
    .min(1, 'Informe o desconto.')
    .refine((value) => {
      const percent = Number(value.replace(',', '.'));
      return Number.isFinite(percent) && percent >= 0 && percent <= 100;
    }, 'O desconto deve ser um número entre 0 e 100.'),
});

type SettingsForm = z.infer<typeof schema>;

/**
 * A configuração de cobrança do estúdio. Uma tela para um campo, e não um campo
 * dentro de /admin/plans: desconto não é atributo de plano nenhum (SPEC-012).
 */
export function SettingsPage() {
  const settings = useBillingSettings();
  const updateSettings = useUpdateBillingSettings();
  const toast = useToast();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<SettingsForm>({
    resolver: zodResolver(schema),
    defaultValues: { multiPlanDiscountPercent: '' },
  });

  // O valor só existe depois da resposta; sem isso o campo abriria vazio e um
  // salvar apressado gravaria o vazio por cima do que estava configurado.
  useEffect(() => {
    if (settings.data) {
      reset({ multiPlanDiscountPercent: String(settings.data.multiPlanDiscountPercent) });
    }
  }, [settings.data, reset]);

  return (
    <section className="max-w-lg space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Configuração de cobrança</h1>

      <Toast message={toast.message} serial={toast.serial} />

      <ListFeedback state={settings} loading="Carregando a configuração…" />

      {settings.data && (
        <form
          onSubmit={handleSubmit((values) =>
            updateSettings.mutate(
              { multiPlanDiscountPercent: Number(values.multiPlanDiscountPercent.replace(',', '.')) },
              { onSuccess: () => toast.show('Configuração salva.') },
            ),
          )}
          noValidate
          className="space-y-4"
        >
          <div className="space-y-1">
            <Label htmlFor="multiPlanDiscountPercent">Desconto para múltiplos planos (%)</Label>
            <Input
              id="multiPlanDiscountPercent"
              inputMode="decimal"
              aria-invalid={errors.multiPlanDiscountPercent ? true : undefined}
              {...register('multiPlanDiscountPercent')}
            />
            {errors.multiPlanDiscountPercent && (
              <p className="text-sm text-red-600">{errors.multiPlanDiscountPercent.message}</p>
            )}
            <p className="text-sm text-slate-600">
              Aplicado sobre a soma das mensalidades a partir do segundo plano do aluno. Com 0, cada plano é
              cobrado pelo valor cheio.
            </p>
          </div>

          {updateSettings.error != null && (
            <p role="alert" className="text-sm text-red-600">
              {apiMessage(updateSettings.error)}
            </p>
          )}

          <Button type="submit" disabled={updateSettings.isPending}>
            {updateSettings.isPending ? 'Salvando…' : 'Salvar'}
          </Button>
        </form>
      )}
    </section>
  );
}
