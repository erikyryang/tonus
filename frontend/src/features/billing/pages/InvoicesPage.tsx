import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ListFeedback } from '@/components/ListFeedback';
import { Pagination } from '@/components/Pagination';
import { Toast, useToast } from '@/components/Toast';
import { usePageClamp } from '@/components/usePageClamp';
import { Button } from '@/components/ui/button';
import { formatDate, formatMoney, formatReferenceMonth } from '@/lib/formatters';
import { parsePageParam } from '@/lib/listParams';
import { cn } from '@/lib/utils';
import type { Invoice, InvoiceStatus } from '../api/billingApi';
import { useInvoices, useRegisterPayment } from '../api/billingQueries';
import { InvoiceStatusBadge } from '../components/InvoiceStatusBadge';
import { RegisterPaymentDialog } from '../components/RegisterPaymentDialog';
import { studentLabel } from '../lib/studentLabel';

/**
 * "Todas" é o default; "Atrasadas" é o atalho que a spec pede em destaque.
 * `empty` acompanha o filtro porque "Nenhuma fatura" sob "Atrasadas" leria como
 * "não existe fatura nenhuma" — o oposto do que o ADMIN precisa concluir.
 */
const FILTERS: Array<{ label: string; status?: InvoiceStatus; empty: string }> = [
  { label: 'Todas', empty: 'Nenhuma fatura registrada ainda.' },
  { label: 'Abertas', status: 'OPEN', empty: 'Nenhuma fatura aberta.' },
  { label: 'Atrasadas', status: 'OVERDUE', empty: 'Nenhuma fatura atrasada.' },
  { label: 'Pagas', status: 'PAID', empty: 'Nenhuma fatura paga.' },
];

const STATUSES: InvoiceStatus[] = ['OPEN', 'PAID', 'OVERDUE'];

/**
 * Filtro e página moram na URL, e não em useState — mesma decisão da agenda:
 * recarregar, voltar no histórico e mandar o link de "as atrasadas" para alguém
 * continuam funcionando.
 */
export function InvoicesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseStatus(searchParams.get('status'));
  const page = parsePageParam(searchParams.get('page'));

  const [pending, setPending] = useState<Invoice | null>(null);
  const toast = useToast();

  const invoices = useInvoices({ status, page });
  const registerPayment = useRegisterPayment();

  function closeDialog() {
    setPending(null);
    registerPayment.reset();
  }

  const data = invoices.data;
  const clamping = usePageClamp(data, (next) => setSearchParams(queryFor(status, next), { replace: true }));

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Faturas</h1>

      <div role="group" aria-label="Filtrar por status" className="flex gap-2">
        {FILTERS.map((filter) => (
          <Button
            key={filter.label}
            size="sm"
            variant={filter.status === status ? 'default' : 'outline'}
            aria-pressed={filter.status === status}
            // Trocar de filtro volta para a primeira página: a página 3 das
            // atrasadas raramente existe quando o filtro era "todas".
            onClick={() => setSearchParams(queryFor(filter.status, 0))}
          >
            {filter.label}
          </Button>
        ))}
      </div>

      <Toast message={toast.message} serial={toast.serial} />

      {/* Enquanto o recuo não acontece, o vazio da página inexistente mentiria. */}
      <ListFeedback
        state={invoices}
        loading="Carregando as faturas…"
        empty={data && data.content.length === 0 && !clamping ? emptyMessageFor(status) : undefined}
      />

      {data && data.content.length > 0 && (
        <>
          <InvoiceTable invoices={data.content} onRegisterPayment={setPending} />
          <Pagination
            page={data.number}
            totalPages={data.totalPages}
            onChange={(next) => setSearchParams(queryFor(status, next))}
          />
        </>
      )}

      {pending && (
        <RegisterPaymentDialog
          invoice={pending}
          pending={registerPayment.isPending}
          error={registerPayment.error}
          onClose={closeDialog}
          onConfirm={({ paymentDate }) =>
            registerPayment.mutate(
              { id: pending.id, paymentDate },
              {
                onSuccess: () => {
                  // "será avisado", não "recebeu": a entrega do WhatsApp é
                  // assíncrona (evento + reentrega). No instante da resposta
                  // HTTP nada foi entregue ainda, e o toast não pode mentir.
                  toast.show('Baixa registrada. O aluno será avisado por WhatsApp.');
                  closeDialog();
                },
              },
            )
          }
        />
      )}
    </section>
  );
}

function InvoiceTable({
  invoices,
  onRegisterPayment,
}: {
  invoices: Invoice[];
  onRegisterPayment: (invoice: Invoice) => void;
}) {
  return (
    <table className="w-full border-collapse text-sm">
      <caption className="sr-only">Faturas</caption>
      <thead>
        <tr className="border-b border-slate-200 text-left text-slate-500">
          <th scope="col" className="px-3 py-2 font-medium">
            Aluno
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            Competência
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            Valor
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            Vencimento
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            Status
          </th>
          <th scope="col" className="px-3 py-2">
            <span className="sr-only">Ações</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {invoices.map((invoice) => (
          <tr
            key={invoice.id}
            // O gancho do destaque da linha atrasada, e o que o teste observa:
            // a cor sozinha não é asserção nem sinal acessível.
            data-status={invoice.status}
            className={cn(
              'border-b border-slate-100',
              invoice.status === 'OVERDUE' && 'bg-red-50',
            )}
          >
            <td className="px-3 py-2 text-slate-900">{studentLabel(invoice)}</td>
            <td className="px-3 py-2">{formatReferenceMonth(invoice.referenceMonth)}</td>
            <td className="px-3 py-2">{formatMoney(invoice.amount)}</td>
            <td className="px-3 py-2">{formatDate(invoice.dueDate)}</td>
            <td className="px-3 py-2">
              <InvoiceStatusBadge status={invoice.status} />
              {invoice.paymentDate && (
                <span className="ml-2 text-xs text-slate-500">pago em {formatDate(invoice.paymentDate)}</span>
              )}
            </td>
            <td className="px-3 py-2 text-right">
              {/* Pagamento é imutável no back: PAGA não tem ação a oferecer. */}
              {invoice.status !== 'PAID' && (
                <Button
                  size="sm"
                  variant="outline"
                  aria-label={`Dar baixa na fatura de ${studentLabel(invoice)}`}
                  onClick={() => onRegisterPayment(invoice)}
                >
                  Dar baixa
                </Button>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/**
 * A query string canônica da tela. Só o que foge do default aparece: sem
 * filtro e na primeira página a URL fica limpa, e /admin/invoices continua
 * sendo o link que se manda para alguém.
 */
function queryFor(status: InvoiceStatus | undefined, page: number): URLSearchParams {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (page > 0) params.set('page', String(page));
  return params;
}

function emptyMessageFor(status: InvoiceStatus | undefined): string {
  return FILTERS.find((filter) => filter.status === status)?.empty ?? FILTERS[0]!.empty;
}

function parseStatus(value: string | null): InvoiceStatus | undefined {
  return STATUSES.find((status) => status === value);
}
