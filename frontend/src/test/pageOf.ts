import type { Page } from '@/lib/page';

/** O recorte do Page do Spring que as listagens devolvem, com defaults de uma página só. */
export function pageOf<T>(content: T[], overrides: Partial<Page<T>> = {}): Page<T> {
  return {
    content,
    number: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    ...overrides,
  };
}
