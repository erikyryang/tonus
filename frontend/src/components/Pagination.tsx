import { Button } from '@/components/ui/button';

/**
 * Nasceu na tela de faturas e virou compartilhado quando a sexta listagem
 * paginada apareceu: todas as listagens do backend são paginadas (regra da
 * raiz), então a barra é a mesma em todas.
 */
export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  /** Índice da página, base zero — como o `number` do Page do Spring. */
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center gap-3 text-sm text-slate-600">
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
