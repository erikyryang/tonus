import { Button } from '@/components/ui/button';

/**
 * Uma opção do filtro de situação. `active: undefined` é "todos" — o back lê o
 * parâmetro ausente assim.
 *
 * O vazio anda junto do filtro porque só ele sabe o que a lista vazia significa:
 * "Nenhum aluno" sob "Inativos" leria como "não existe aluno nenhum".
 *
 * Os rótulos ficam em cada tela, e não num gerador aqui: "Ativos" para aluno e
 * "Ativas" para assinatura é concordância, não configuração.
 */
export interface StatusFilterOption {
  label: string;
  active?: boolean;
  empty: string;
}

export function emptyMessageOf(filters: StatusFilterOption[], active: boolean | undefined): string {
  return filters.find((filter) => filter.active === active)?.empty ?? filters[0]!.empty;
}

/**
 * A barra de situação das listagens do ADMIN. Nasceu igual em alunos,
 * professores e assinaturas: as três copiavam o mesmo `role="group"`, o mesmo
 * `aria-pressed` e a mesma volta para a primeira página.
 */
export function StatusFilter({
  filters,
  active,
  onChange,
}: {
  filters: StatusFilterOption[];
  active: boolean | undefined;
  onChange: (active: boolean | undefined) => void;
}) {
  return (
    <div role="group" aria-label="Filtrar por situação" className="flex gap-2">
      {filters.map((filter) => (
        <Button
          key={filter.label}
          size="sm"
          variant={filter.active === active ? 'default' : 'outline'}
          aria-pressed={filter.active === active}
          // Trocar de filtro volta para a primeira página: a página 3 dos
          // inativos raramente existe quando o filtro era "ativos".
          onClick={() => onChange(filter.active)}
        >
          {filter.label}
        </Button>
      ))}
    </div>
  );
}
