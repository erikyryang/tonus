import { ErrorNotice } from '@/components/ErrorNotice';
import { apiMessage } from '@/lib/apiError';

/** The slice of the useQuery result that listings use to explain themselves. */
export interface ListQueryState {
  isPending: boolean;
  isFetching: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => void;
}

/**
 * Loading, refreshing, failed and empty — the four states every listing has
 * and that, hand-written in each screen, drifted apart with every new one.
 *
 * The empty state comes from outside because only the screen knows what it means: "no students"
 * under the "Inactive" filter would read as "there are no students at all".
 */
export function ListFeedback({
  state,
  loading,
  empty,
}: {
  state: ListQueryState;
  loading: string;
  /** Empty-state text, or undefined when there is content to show. */
  empty?: string;
}) {
  return (
    <>
      {state.isPending && <p className="text-sm text-muted-foreground">{loading}</p>}

      {!state.isPending && state.isFetching && <p className="text-sm text-muted-foreground">Atualizando…</p>}

      {state.isError && <ErrorNotice message={apiMessage(state.error)} onRetry={state.refetch} />}

      {empty !== undefined && <p className="text-sm text-muted-foreground">{empty}</p>}
    </>
  );
}
