/**
 * Filtro de situação e página moram na URL, e não em useState: recarregar,
 * voltar no histórico e mandar o link de "os inativos" para alguém continuam
 * funcionando. Mesma decisão da agenda e das faturas.
 *
 * "Todos" tem valor próprio (`all`) porque o default é "ativos": sem ele,
 * ausência de parâmetro significaria as duas coisas ao mesmo tempo.
 */
export function parseActiveParam(value: string | null): boolean | undefined {
  if (value === 'false') return false;
  if (value === 'all') return undefined;
  return true;
}

export function parsePageParam(value: string | null): number {
  const page = Number(value);
  return Number.isInteger(page) && page > 0 ? page : 0;
}

/** A query string canônica: só o que foge do default aparece. */
export function listQuery(active: boolean | undefined, page: number): URLSearchParams {
  const params = new URLSearchParams();
  if (active === false) params.set('active', 'false');
  if (active === undefined) params.set('active', 'all');
  if (page > 0) params.set('page', String(page));
  return params;
}

/** A mesma canonicalização para as listagens que só paginam: página 1 é a URL limpa. */
export function pageQuery(page: number): URLSearchParams {
  const params = new URLSearchParams();
  if (page > 0) params.set('page', String(page));
  return params;
}
