/**
 * O recorte do `Page` do Spring que o front consome. As listagens do backend
 * são todas paginadas (regra da raiz), e a resposta traz bem mais campos
 * (`pageable`, `sort`, `first`, `empty`) — espelhar só o que a tela usa é o
 * mesmo critério dos outros DTOs: o tipo documenta o que o front depende, não
 * o que o Jackson emite.
 */
export interface Page<T> {
  content: T[];
  /** Índice da página, base zero. */
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
