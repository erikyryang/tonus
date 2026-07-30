import { useEffect, useRef } from 'react';
import type { Page } from '@/lib/page';

/**
 * Recua quando a página deixa de existir debaixo de quem está nela.
 *
 * Acontece de verdade: o ADMIN está na última página dos ativos, desativa o
 * único registro dela, e aquela página some do resultado. Sem recuar, a tela
 * diria "nenhum aluno ativo" com a página 1 cheia. Cobre também `?page=` fora
 * do intervalo digitado na URL.
 *
 * O alvo é um número (ou null), e não uma função, para o efeito depender só de
 * primitivos. O callback mora num ref pela mesma razão: as telas o montam
 * inline (ele carrega o filtro atual), então a identidade muda a cada render e,
 * na lista de dependências, faria o efeito navegar em loop.
 *
 * Devolve se está recuando — enquanto isso o vazio da página inexistente
 * mentiria, e quem chama o esconde.
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
    // replace: a página inexistente não merece entrada no histórico — voltar
    // nela só provocaria o mesmo recuo de novo.
    if (target != null) handler.current(target);
  }, [target]);

  return target != null;
}
