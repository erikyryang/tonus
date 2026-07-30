import { HttpResponse } from 'msw';

/** Problem Details (RFC 9457) — o formato de erro que o back devolve sempre. */
export function problem(status: number, title: string, detail: string) {
  return HttpResponse.json(
    { type: 'about:blank', title, status, detail },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}
