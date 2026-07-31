import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ListFeedback } from '@/components/ListFeedback';
import { Pagination } from '@/components/Pagination';
import { Toast, useToast } from '@/components/Toast';
import { usePageClamp } from '@/components/usePageClamp';
import { ActionMenu } from '@/components/ui/actionMenu';
import { Button } from '@/components/ui/button';
import { pageQuery, parsePageParam } from '@/lib/listParams';
import { formatDate } from '@/lib/formatters';
import type { Holiday } from '../api/schedulingApi';
import { useCreateHoliday, useDeleteHoliday, useHolidays } from '../api/schedulingQueries';
import { HolidayFormDialog } from '../components/HolidayFormDialog';

type Pending = { kind: 'create' } | { kind: 'delete'; holiday: Holiday };

export function HolidaysPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePageParam(searchParams.get('page'));

  const [pending, setPending] = useState<Pending | null>(null);
  const toast = useToast();

  const holidays = useHolidays(page);
  const createHoliday = useCreateHoliday();
  const deleteHoliday = useDeleteHoliday();

  function closeDialog() {
    setPending(null);
    createHoliday.reset();
    deleteHoliday.reset();
  }

  function succeed(message: string) {
    toast.show(message);
    closeDialog();
  }

  const data = holidays.data;
  const clamping = usePageClamp(data, (next) => setSearchParams(pageQuery(next), { replace: true }));

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Feriados</h1>
        <Button onClick={() => setPending({ kind: 'create' })}>Novo feriado</Button>
      </div>

      <Toast message={toast.message} serial={toast.serial} />

      <ListFeedback
        state={holidays}
        loading="Carregando os feriados…"
        empty={data && data.content.length === 0 && !clamping ? 'Nenhum feriado cadastrado.' : undefined}
      />

      {data && data.content.length > 0 && (
        <>
          <table className="w-full border-collapse text-sm">
            <caption className="sr-only">Feriados</caption>
            <thead>
              <tr className="border-b border-slate-200 text-left text-slate-500">
                <th scope="col" className="px-3 py-2 font-medium">
                  Data
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Descrição
                </th>
                <th scope="col" className="px-3 py-2">
                  <span className="sr-only">Ações</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((holiday) => (
                <tr key={holiday.id} className="border-b border-slate-100">
                  <td className="px-3 py-2 text-slate-900">{formatDate(holiday.date)}</td>
                  <td className="px-3 py-2">{holiday.description}</td>
                  <td className="px-3 py-2 text-right">
                    {/* Não existe edição de feriado no back: data ou descrição
                        erradas se resolvem apagando e cadastrando de novo. */}
                    <ActionMenu
                      label={`Ações de ${holiday.description}`}
                      actions={[{ label: 'Excluir', onSelect: () => setPending({ kind: 'delete', holiday }) }]}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination
            page={data.number}
            totalPages={data.totalPages}
            onChange={(next) => setSearchParams(pageQuery(next))}
          />
        </>
      )}

      {pending?.kind === 'create' && (
        <HolidayFormDialog
          pending={createHoliday.isPending}
          error={createHoliday.error}
          onClose={closeDialog}
          onSubmit={(values) =>
            createHoliday.mutate(values, {
              // "serão avisados", não "receberam": a entrega do WhatsApp é
              // assíncrona (evento + reentrega at-least-once).
              onSuccess: () => succeed('Feriado cadastrado. Os alunos ativos serão avisados por WhatsApp.'),
            })
          }
        />
      )}

      {pending?.kind === 'delete' && (
        <ConfirmDialog
          title="Excluir feriado"
          confirmLabel="Excluir"
          pendingLabel="Excluindo…"
          pending={deleteHoliday.isPending}
          error={deleteHoliday.error}
          onClose={closeDialog}
          onConfirm={() =>
            deleteHoliday.mutate(pending.holiday.id, { onSuccess: () => succeed('Feriado excluído.') })
          }
        >
          <p>
            As aulas de <strong>{formatDate(pending.holiday.date)}</strong> voltam para a agenda. Quem já foi
            avisado do feriado por WhatsApp não recebe desmentido — o aviso precisa ser dado à mão.
          </p>
        </ConfirmDialog>
      )}
    </section>
  );
}
