import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ListFeedback } from '@/components/ListFeedback';
import { Pagination } from '@/components/Pagination';
import { Toast, useToast } from '@/components/Toast';
import { usePageClamp } from '@/components/usePageClamp';
import { ActionMenu } from '@/components/ui/actionMenu';
import { Button } from '@/components/ui/button';
import { nameById, useStudentsLookup } from '@/features/registration/api/registrationQueries';
import { formatMoney } from '@/lib/formatters';
import { listQuery, parseActiveParam, parsePageParam } from '@/lib/listParams';
import type { Subscription } from '../api/billingApi';
import {
  useCreateSubscription,
  useSetSubscriptionActive,
  useSubscriptions,
  useUpdateSubscription,
} from '../api/billingQueries';
import { SubscriptionFormDialog } from '../components/SubscriptionFormDialog';

const FILTERS: Array<{ label: string; active?: boolean; empty: string }> = [
  { label: 'Ativas', active: true, empty: 'Nenhuma assinatura ativa.' },
  { label: 'Inativas', active: false, empty: 'Nenhuma assinatura inativa.' },
  { label: 'Todas', empty: 'Nenhuma assinatura cadastrada.' },
];

type Pending =
  | { kind: 'create' }
  | { kind: 'edit'; subscription: Subscription; studentName: string }
  | { kind: 'toggle'; subscription: Subscription; studentName: string };

export function SubscriptionsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const active = parseActiveParam(searchParams.get('active'));
  const page = parsePageParam(searchParams.get('page'));

  const [pending, setPending] = useState<Pending | null>(null);
  const toast = useToast();

  const subscriptions = useSubscriptions({ active, page });
  const students = useStudentsLookup();

  const createSubscription = useCreateSubscription();
  const updateSubscription = useUpdateSubscription();
  const setActive = useSetSubscriptionActive();

  function closeDialog() {
    setPending(null);
    createSubscription.reset();
    updateSubscription.reset();
    setActive.reset();
  }

  function succeed(message: string) {
    toast.show(message);
    closeDialog();
  }

  const data = subscriptions.data;
  const clamping = usePageClamp(data, (next) => setSearchParams(listQuery(active, next), { replace: true }));
  const studentNames = nameById(students.data);
  const activeStudents = (students.data ?? []).filter((student) => student.active);
  const nameOf = (studentId: number) => studentNames.get(studentId) ?? `Aluno #${studentId}`;

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Assinaturas</h1>
        <Button onClick={() => setPending({ kind: 'create' })}>Nova assinatura</Button>
      </div>

      <div role="group" aria-label="Filtrar por situação" className="flex gap-2">
        {FILTERS.map((filter) => (
          <Button
            key={filter.label}
            size="sm"
            variant={filter.active === active ? 'default' : 'outline'}
            aria-pressed={filter.active === active}
            onClick={() => setSearchParams(listQuery(filter.active, 0))}
          >
            {filter.label}
          </Button>
        ))}
      </div>

      <Toast message={toast.message} />

      <ListFeedback
        state={subscriptions}
        loading="Carregando as assinaturas…"
        empty={data && data.content.length === 0 && !clamping ? emptyMessageFor(active) : undefined}
      />

      {data && data.content.length > 0 && (
        <>
          <table className="w-full border-collapse text-sm">
            <caption className="sr-only">Assinaturas</caption>
            <thead>
              <tr className="border-b border-slate-200 text-left text-slate-500">
                <th scope="col" className="px-3 py-2 font-medium">
                  Aluno
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Valor mensal
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Vencimento
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
              {data.content.map((subscription) => (
                <tr key={subscription.id} data-active={subscription.active} className="border-b border-slate-100">
                  <td className="px-3 py-2 text-slate-900">{nameOf(subscription.studentId)}</td>
                  <td className="px-3 py-2">{formatMoney(subscription.monthlyAmount)}</td>
                  <td className="px-3 py-2">dia {subscription.dueDay}</td>
                  <td className="px-3 py-2">{subscription.active ? 'Ativa' : 'Inativa'}</td>
                  <td className="px-3 py-2 text-right">
                    <ActionMenu
                      label={`Ações de ${nameOf(subscription.studentId)}`}
                      actions={[
                        {
                          label: 'Editar',
                          onSelect: () =>
                            setPending({
                              kind: 'edit',
                              subscription,
                              studentName: nameOf(subscription.studentId),
                            }),
                        },
                        {
                          label: subscription.active ? 'Desativar' : 'Ativar',
                          onSelect: () =>
                            setPending({
                              kind: 'toggle',
                              subscription,
                              studentName: nameOf(subscription.studentId),
                            }),
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
        <SubscriptionFormDialog
          title="Nova assinatura"
          students={activeStudents}
          pending={createSubscription.isPending}
          error={createSubscription.error}
          onClose={closeDialog}
          onSubmit={(values) =>
            createSubscription.mutate(values, { onSuccess: () => succeed('Assinatura cadastrada.') })
          }
        />
      )}

      {pending?.kind === 'edit' && (
        <SubscriptionFormDialog
          title="Editar assinatura"
          students={activeStudents}
          studentLocked
          defaultValues={{
            studentId: pending.subscription.studentId,
            // O campo é de texto e em pt-BR: 350.5 vira "350,50".
            monthlyAmount: pending.subscription.monthlyAmount.toFixed(2).replace('.', ','),
            dueDay: pending.subscription.dueDay,
          }}
          pending={updateSubscription.isPending}
          error={updateSubscription.error}
          onClose={closeDialog}
          onSubmit={(values) =>
            updateSubscription.mutate(
              { id: pending.subscription.id, input: { ...values, studentId: pending.subscription.studentId } },
              { onSuccess: () => succeed('Assinatura atualizada.') },
            )
          }
        />
      )}

      {pending?.kind === 'toggle' && (
        <ConfirmDialog
          title={pending.subscription.active ? 'Desativar assinatura' : 'Ativar assinatura'}
          confirmLabel={pending.subscription.active ? 'Desativar' : 'Ativar'}
          pendingLabel={pending.subscription.active ? 'Desativando…' : 'Ativando…'}
          pending={setActive.isPending}
          error={setActive.error}
          onClose={closeDialog}
          onConfirm={() =>
            setActive.mutate(
              { id: pending.subscription.id, active: !pending.subscription.active },
              {
                onSuccess: () =>
                  succeed(pending.subscription.active ? 'Assinatura desativada.' : 'Assinatura ativada.'),
              },
            )
          }
        >
          {pending.subscription.active ? (
            <p>
              <strong>{pending.studentName}</strong> deixa de gerar fatura nos próximos meses. As faturas já
              emitidas continuam como estão.
            </p>
          ) : (
            <p>
              <strong>{pending.studentName}</strong> volta a gerar fatura mensal a partir da próxima rodada de
              cobrança.
            </p>
          )}
        </ConfirmDialog>
      )}
    </section>
  );
}

function emptyMessageFor(active: boolean | undefined): string {
  return FILTERS.find((filter) => filter.active === active)?.empty ?? FILTERS[0]!.empty;
}
