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
 * "All" is the default; "Overdue" is the shortcut the spec asks to highlight.
 * `empty` travels with the filter because "No invoices" under "Overdue" would read as
 * "there are no invoices at all" — the opposite of what the ADMIN needs to conclude.
 */
const FILTERS: Array<{ label: string; status?: InvoiceStatus; empty: string }> = [
  { label: 'Todas', empty: 'Nenhuma fatura registrada ainda.' },
  { label: 'Abertas', status: 'OPEN', empty: 'Nenhuma fatura aberta.' },
  { label: 'Atrasadas', status: 'OVERDUE', empty: 'Nenhuma fatura atrasada.' },
  { label: 'Pagas', status: 'PAID', empty: 'Nenhuma fatura paga.' },
];

const STATUSES: InvoiceStatus[] = ['OPEN', 'PAID', 'OVERDUE'];

/**
 * Filter and page live in the URL, not in useState — same decision as the schedule:
 * reloading, going back in history and sending someone the "overdue ones" link
 * keep working.
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
      <h1 className="text-xl font-semibold text-foreground">Faturas</h1>

      <div role="group" aria-label="Filtrar por status" className="flex gap-2">
        {FILTERS.map((filter) => (
          <Button
            key={filter.label}
            size="sm"
            variant={filter.status === status ? 'default' : 'outline'}
            aria-pressed={filter.status === status}
            // Switching filters goes back to the first page: page 3 of the
            // overdue ones rarely exists when the filter was "all".
            onClick={() => setSearchParams(queryFor(filter.status, 0))}
          >
            {filter.label}
          </Button>
        ))}
      </div>

      <Toast message={toast.message} serial={toast.serial} />

      {/* While the step-back hasn't happened, the nonexistent page's empty state would lie. */}
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
                  // "will be notified", not "received": WhatsApp delivery is
                  // asynchronous (event + redelivery). At the moment of the HTTP
                  // response nothing has been delivered yet, and the toast can't lie.
                  toast.show('Renovação registrada. O aluno será avisado por WhatsApp.');
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
        <tr className="border-b border-border text-left text-muted-foreground">
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
            // The hook for the overdue row's highlight, and what the test observes:
            // color alone is neither an assertion nor an accessible signal.
            data-status={invoice.status}
            className={cn(
              'border-b border-border',
              invoice.status === 'OVERDUE' && 'bg-danger-subtle/50',
            )}
          >
            <td className="px-3 py-2 text-foreground">{studentLabel(invoice)}</td>
            <td className="px-3 py-2">{formatReferenceMonth(invoice.referenceMonth)}</td>
            <td className="px-3 py-2">{formatMoney(invoice.amount)}</td>
            <td className="px-3 py-2">{formatDate(invoice.dueDate)}</td>
            <td className="px-3 py-2">
              <InvoiceStatusBadge status={invoice.status} />
              {invoice.paymentDate && (
                <span className="ml-2 text-xs text-muted-foreground">pago em {formatDate(invoice.paymentDate)}</span>
              )}
            </td>
            <td className="px-3 py-2 text-right">
              {/* Payment is immutable in the back: PAID has no action to offer.
                  "Renew": a paid invoice is a renewed cycle, same endpoint as always
                  (POST /invoices/{id}/payment) — only the text changes (SPEC-004/008). */}
              {invoice.status !== 'PAID' && (
                <Button
                  size="sm"
                  variant="outline"
                  aria-label={`Renovar a fatura de ${studentLabel(invoice)}`}
                  onClick={() => onRegisterPayment(invoice)}
                >
                  Renovar
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
 * The screen's canonical query string. Only what deviates from the default appears: with no
 * filter and on the first page the URL stays clean, and /admin/invoices remains
 * the link that gets sent to someone.
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
