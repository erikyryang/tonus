import { Button } from '@/components/ui/button';

/**
 * A status filter option. `active: undefined` is "all" — that's how the back
 * reads the missing parameter.
 *
 * The empty-state text travels with the filter because only it knows what the empty list means:
 * "No students" under "Inactive" would read as "there are no students at all".
 *
 * The labels stay in each screen, and not in a generator here: "Ativos" for student and
 * "Ativas" for subscription is agreement, not configuration.
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
 * The status bar for ADMIN listings. Born identical in students,
 * teachers and subscriptions: all three copied the same `role="group"`, the same
 * `aria-pressed` and the same return to the first page.
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
          // Switching filters goes back to the first page: page 3 of the
          // inactive ones rarely exists when the filter was "active".
          onClick={() => onChange(filter.active)}
        >
          {filter.label}
        </Button>
      ))}
    </div>
  );
}
