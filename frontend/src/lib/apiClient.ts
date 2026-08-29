/**
 * Único ponto de rede do front. Sessão vive em cookie httpOnly: nada de
 * Authorization header, nada de token em storage — só credentials: 'include'
 * contra a mesma origem (/api é proxy do Vite em dev e do Caddy em prod).
 *
 * Exceção deliberada: CSRF. O backend exige o cookie XSRF-TOKEN (não-httpOnly,
 * de propósito) ecoado como header X-XSRF-TOKEN em todo método que muda estado
 * — o padrão double-submit. É o único token que o JS lê.
 */
const BASE_URL = '/api';
const REFRESH_PATH = '/auth/refresh';

/**
 * O token CSRF do servidor, para ecoar como header. O cookie é não-httpOnly
 * por design (é o padrão double-submit: o valor não é segredo, o que o protege
 * é o same-origin — uma página cross-site não consegue lê-lo).
 */
function csrfToken(): string | undefined {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match?.[1];
}

/** 401 nesses caminhos é resposta de negócio, não sessão expirada. */
const NO_REFRESH_PATHS = new Set(['/auth/login', '/auth/logout', REFRESH_PATH]);

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly detail?: string;
  readonly problem?: ProblemDetail;

  constructor(status: number, problem?: ProblemDetail) {
    super(problem?.detail ?? problem?.title ?? `Erro ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.detail = problem?.detail;
    this.problem = problem;
  }
}

let unauthorizedHandler: () => void = () => {};

/** O app registra aqui o "limpar cache e ir para /login". */
export function setUnauthorizedHandler(handler: () => void) {
  unauthorizedHandler = handler;
}

let refreshPromise: Promise<boolean> | null = null;

/** Requisições concorrentes com 401 compartilham UMA promise de refresh. */
function refreshSession(): Promise<boolean> {
  const token = csrfToken();
  refreshPromise ??= fetch(`${BASE_URL}${REFRESH_PATH}`, {
    method: 'POST',
    credentials: 'include',
    headers: token === undefined ? {} : { 'X-XSRF-TOKEN': token },
  })
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshPromise = null;
    });
  return refreshPromise;
}

async function request<T>(path: string, init: RequestInit = {}, allowRefresh = true): Promise<T> {
  const method = init.method ?? 'GET';
  const token = csrfToken();
  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      ...(init.body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(method !== 'GET' && token !== undefined ? { 'X-XSRF-TOKEN': token } : {}),
      ...init.headers,
    },
  });

  if (response.status === 401 && allowRefresh && !NO_REFRESH_PATHS.has(path)) {
    if (await refreshSession()) {
      return request<T>(path, init, false);
    }
    unauthorizedHandler();
    throw await toApiError(response);
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  return (await readBody(response)) as T;
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const text = await response.text();
  return text.length === 0 ? undefined : JSON.parse(text);
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const problem = (await response.json()) as ProblemDetail;
    return new ApiError(response.status, problem);
  } catch {
    return new ApiError(response.status);
  }
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
