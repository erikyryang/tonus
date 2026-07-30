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
 * As faturas do aluno logado. Só leitura: dar baixa é do ADMIN, e o aluno não
 * tem como se declarar pago.
 *
 * Cartões, e não tabela: a tela é do celular, e uma tabela de cinco colunas em
 * 360px vira rolagem horizontal.
 */
export function MyInvoicesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePageParam(searchParams.get('page'));

  const invoices = useMyInvoices(page);
  const data = invoices.data;
  const clamping = usePageClamp(data, (next) => setSearchParams(pageQuery(next), { replace: true }));

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Minhas faturas</h1>

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
                // O gancho do destaque, e o que o teste observa: cor sozinha
                // não é sinal acessível.
                data-status={invoice.status}
                className={cn(
                  'flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 bg-white p-4',
                  invoice.status === 'OVERDUE' && 'border-red-200 bg-red-50',
                )}
              >
                <div>
                  <p className="text-sm font-semibold text-slate-900">
                    {formatReferenceMonth(invoice.referenceMonth)}
                  </p>
                  <p className="text-sm text-slate-600">vence em {formatDate(invoice.dueDate)}</p>
                  {invoice.paymentDate && (
                    <p className="text-xs text-slate-500">pago em {formatDate(invoice.paymentDate)}</p>
                  )}
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-sm font-semibold text-slate-900">{formatMoney(invoice.amount)}</span>
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
