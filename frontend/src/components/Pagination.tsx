import { Button } from '@/components/ui/button';

/**
 * Born on the invoices screen and became shared when the sixth paginated
 * listing showed up: every backend listing is paginated (root rule), so
 * the bar is the same across all of them.
 */
export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  /** Page index, zero-based — like Spring's Page `number`. */
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center gap-3 text-sm text-muted-foreground">
      <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Página anterior
      </Button>
      <span>
        Página {page + 1} de {totalPages}
      </span>
      <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        Próxima página
      </Button>
    </div>
  );
}
