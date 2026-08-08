import { useEffect, useRef } from 'react';
import type { Page } from '@/lib/page';

/**
 * Steps back when the page it's on stops existing.
 *
 * It really happens: the ADMIN is on the last page of the active ones, deactivates the
 * only record on it, and that page vanishes from the result. Without stepping back, the screen
 * would say "no active students" with page 1 full. Also covers `?page=` outside
 * the range typed into the URL.
 *
 * The target is a number (or null), not a function, so the effect depends only on
 * primitives. The callback lives in a ref for the same reason: screens mount it
 * inline (it carries the current filter), so its identity changes on every render and,
 * in the dependency list, would make the effect navigate in a loop.
 *
 * Returns whether it's stepping back — while that happens, the empty state of the nonexistent page
 * would lie, and the caller hides it.
 */
export function usePageClamp<T>(data: Page<T> | undefined, onClamp: (page: number) => void): boolean {
  const handler = useRef(onClamp);

  useEffect(() => {
    handler.current = onClamp;
  });

  const target =
    data != null && data.number > 0 && data.content.length === 0 && data.totalPages > 0
      ? data.totalPages - 1
      : null;

  useEffect(() => {
    // replace: the nonexistent page doesn't deserve a history entry — going back
    // to it would only trigger the same step-back again.
    if (target != null) handler.current(target);
  }, [target]);

  return target != null;
}
