import { useSearchParams } from 'react-router-dom';
import { ListFeedback } from '@/components/ListFeedback';
import { Pagination } from '@/components/Pagination';
import { usePageClamp } from '@/components/usePageClamp';
import { formatDate, formatMoney, formatReferenceMonth } from '@/lib/formatters';
import { pageQuery, parsePageParam } from '@/lib/listParams';
import { cn } from '@/lib/utils';
import { useMyInvoices } from '../api/billingQueries';
import { InvoiceStatusBadge } from '../components/InvoiceStatusBadge';

/**
 * The logged-in student's invoices. Read-only: settling payment is the ADMIN's job, and the student has
 * no way to declare themself as paid.
 *
 * Cards, not a table: the screen is for the phone, and a five-column table at
 * 360px turns into horizontal scrolling.
 */
export function MyInvoicesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePageParam(searchParams.get('page'));

  const invoices = useMyInvoices(page);
  const data = invoices.data;
  const clamping = usePageClamp(data, (next) => setSearchParams(pageQuery(next), { replace: true }));

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold text-foreground">Minhas faturas</h1>

      <ListFeedback
        state={invoices}
        loading="Carregando as suas faturas…"
        empty={data && data.content.length === 0 && !clamping ? 'Nenhuma fatura registrada ainda.' : undefined}
      />

      {data && data.content.length > 0 && (
        <>
          <ul className="space-y-2">
            {data.content.map((invoice) => (
              <li
                key={invoice.id}
                aria-label={`Fatura de ${formatReferenceMonth(invoice.referenceMonth)}`}
                // The hook for the highlight, and what the test observes: color alone
                // is not an accessible signal.
                data-status={invoice.status}
                className={cn(
                  'flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-card p-4',
                  invoice.status === 'OVERDUE' && 'border-danger-emphasis/30 bg-danger-subtle/50',
                )}
              >
                <div>
                  <p className="text-sm font-semibold text-foreground">
                    {formatReferenceMonth(invoice.referenceMonth)}
                  </p>
                  <p className="text-sm text-muted-foreground">vence em {formatDate(invoice.dueDate)}</p>
                  {invoice.paymentDate && (
                    <p className="text-xs text-muted-foreground">pago em {formatDate(invoice.paymentDate)}</p>
                  )}
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-sm font-semibold text-foreground">{formatMoney(invoice.amount)}</span>
                  <InvoiceStatusBadge status={invoice.status} />
                </div>
              </li>
            ))}
          </ul>
          <Pagination
            page={data.number}
            totalPages={data.totalPages}
            onChange={(next) => setSearchParams(pageQuery(next))}
          />
        </>
      )}
    </section>
  );
}
