import { Button } from '@/components/ui/button';
import { apiMessage } from '@/lib/apiError';

/** O recorte do resultado do useQuery que as listagens usam para se explicar. */
export interface ListQueryState {
  isPending: boolean;
  isFetching: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => void;
}

/**
 * Carregando, atualizando, falhou e vazio — os quatro estados que toda
 * listagem tem e que, escritos à mão em cada tela, divergiam a cada nova.
 *
 * O vazio vem de fora porque só a tela sabe o que ele significa: "nenhum aluno"
 * sob o filtro "Inativos" leria como "não existe aluno nenhum".
 */
export function ListFeedback({
  state,
  loading,
  empty,
}: {
  state: ListQueryState;
  loading: string;
  /** Texto do vazio, ou undefined quando há conteúdo a mostrar. */
  empty?: string;
}) {
  return (
    <>
      {state.isPending && <p className="text-sm text-slate-500">{loading}</p>}

      {!state.isPending && state.isFetching && <p className="text-sm text-slate-400">Atualizando…</p>}

      {state.isError && (
        <div className="space-y-2">
          <p role="alert" className="text-sm text-red-600">
            {apiMessage(state.error)}
          </p>
          <Button variant="outline" size="sm" onClick={state.refetch}>
            Tentar de novo
          </Button>
        </div>
      )}

      {empty !== undefined && <p className="text-sm text-slate-500">{empty}</p>}
    </>
  );
}
