import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ListFeedback } from '@/components/ListFeedback';
import { Pagination } from '@/components/Pagination';
import { StatusFilter, type StatusFilterOption, emptyMessageOf } from '@/components/StatusFilter';
import { Toast, useToast } from '@/components/Toast';
import { usePageClamp } from '@/components/usePageClamp';
import { ActionMenu } from '@/components/ui/actionMenu';
import { Button } from '@/components/ui/button';
import { formatMoney } from '@/lib/formatters';
import { listQuery, parseActiveParam, parsePageParam } from '@/lib/listParams';
import type { Plan } from '../api/billingApi';
import { useCreatePlan, usePlans, useSetPlanActive, useUpdatePlan } from '../api/billingQueries';
import { PlanFormDialog } from '../components/PlanFormDialog';

const FILTERS: StatusFilterOption[] = [
  { label: 'Ativos', active: true, empty: 'Nenhum plano ativo.' },
  { label: 'Inativos', active: false, empty: 'Nenhum plano inativo.' },
  { label: 'Todos', empty: 'Nenhum plano cadastrado.' },
];

type Pending =
  | { kind: 'create' }
  | { kind: 'edit'; plan: Plan }
  | { kind: 'toggle'; plan: Plan };

export function PlansPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const active = parseActiveParam(searchParams.get('active'));
  const page = parsePageParam(searchParams.get('page'));

  const [pending, setPending] = useState<Pending | null>(null);
  const toast = useToast();

  const plans = usePlans({ active, page });
  const createPlan = useCreatePlan();
  const updatePlan = useUpdatePlan();
  const setActive = useSetPlanActive();

  function closeDialog() {
    setPending(null);
    createPlan.reset();
    updatePlan.reset();
    setActive.reset();
  }

  function succeed(message: string) {
    toast.show(message);
    closeDialog();
  }

  const data = plans.data;
  const clamping = usePageClamp(data, (next) => setSearchParams(listQuery(active, next), { replace: true }));

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-foreground">Planos</h1>
        <Button onClick={() => setPending({ kind: 'create' })}>Novo plano</Button>
      </div>

      <StatusFilter filters={FILTERS} active={active} onChange={(next) => setSearchParams(listQuery(next, 0))} />

      <Toast message={toast.message} serial={toast.serial} />

      <ListFeedback
        state={plans}
        loading="Carregando os planos…"
        empty={data && data.content.length === 0 && !clamping ? emptyMessageOf(FILTERS, active) : undefined}
      />

      {data && data.content.length > 0 && (
        <>
          <table className="w-full border-collapse text-sm">
            <caption className="sr-only">Planos</caption>
            <thead>
              <tr className="border-b border-border text-left text-muted-foreground">
                <th scope="col" className="px-3 py-2 font-medium">
                  Nome
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Valor mensal
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Alunos
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Situação
                </th>
                <th scope="col" className="px-3 py-2">
                  <span className="sr-only">Ações</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((plan) => (
                <tr key={plan.id} data-active={plan.active} className="border-b border-border">
                  <td className="px-3 py-2 text-foreground">{plan.name}</td>
                  <td className="px-3 py-2">{formatMoney(plan.monthlyPrice)}</td>
                  <td className="px-3 py-2">{plan.activeEnrollments}</td>
                  <td className="px-3 py-2">{plan.active ? 'Ativo' : 'Inativo'}</td>
                  <td className="px-3 py-2 text-right">
                    {/* No deleting: a deactivated plan keeps explaining the
                        invoices issued with it (SPEC-012). */}
                    <ActionMenu
                      label={`Ações de ${plan.name}`}
                      actions={[
                        { label: 'Editar', onSelect: () => setPending({ kind: 'edit', plan }) },
                        {
                          label: plan.active ? 'Desativar' : 'Ativar',
                          onSelect: () => setPending({ kind: 'toggle', plan }),
                        },
                      ]}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination
            page={data.number}
            totalPages={data.totalPages}
            onChange={(next) => setSearchParams(listQuery(active, next))}
          />
        </>
      )}

      {pending?.kind === 'create' && (
        <PlanFormDialog
          title="Novo plano"
          pending={createPlan.isPending}
          error={createPlan.error}
          onClose={closeDialog}
          onSubmit={(values) => createPlan.mutate(values, { onSuccess: () => succeed('Plano cadastrado.') })}
        />
      )}

      {pending?.kind === 'edit' && (
        <PlanFormDialog
          title="Editar plano"
          defaultValues={{
            name: pending.plan.name,
            monthlyPrice: pending.plan.monthlyPrice.toFixed(2).replace('.', ','),
          }}
          pending={updatePlan.isPending}
          error={updatePlan.error}
          onClose={closeDialog}
          onSubmit={(values) =>
            updatePlan.mutate({ id: pending.plan.id, input: values }, { onSuccess: () => succeed('Plano atualizado.') })
          }
        />
      )}

      {pending?.kind === 'toggle' && (
        <ConfirmDialog
          title={pending.plan.active ? 'Desativar plano' : 'Ativar plano'}
          confirmLabel={pending.plan.active ? 'Desativar' : 'Ativar'}
          pendingLabel={pending.plan.active ? 'Desativando…' : 'Ativando…'}
          pending={setActive.isPending}
          error={setActive.error}
          onClose={closeDialog}
          onConfirm={() =>
            setActive.mutate(
              { id: pending.plan.id, active: !pending.plan.active },
              { onSuccess: () => succeed(pending.plan.active ? 'Plano desativado.' : 'Plano ativado.') },
            )
          }
        >
          {/* Deactivating with an active enrollment is allowed (SPEC-012); the price is
              saying the size of the damage upfront, not hiding the button. */}
          {pending.plan.active ? (
            <p>
              <strong>{pending.plan.name}</strong> sai das listagens e das novas matrículas.
              {pending.plan.activeEnrollments > 0 && (
                <>
                  {' '}
                  <strong>
                    {pending.plan.activeEnrollments}{' '}
                    {pending.plan.activeEnrollments === 1 ? 'aluno deixa' : 'alunos deixam'} de ser cobrados
                  </strong>{' '}
                  por ele na próxima fatura. As matrículas continuam registradas.
                </>
              )}
            </p>
          ) : (
            <p>
              <strong>{pending.plan.name}</strong> volta às listagens e pode receber matrículas de novo. Quem já
              está matriculado volta a ser cobrado.
            </p>
          )}
        </ConfirmDialog>
      )}
    </section>
  );
}
