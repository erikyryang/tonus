import { useCallback, useEffect, useRef, useState } from 'react';

const TIMEOUT_MS = 4000;

/**
 * Confirmação efêmera do que acabou de acontecer.
 *
 * A mensagem carrega um número de série, e não só o texto: desativar dois
 * alunos seguidos mostra "Aluno desativado." duas vezes, e sem o número o
 * React reconheceria o mesmo estado, não renderizaria de novo e o segundo aviso
 * herdaria o relógio do primeiro — sumiria quase na hora em que apareceu.
 */
export function useToast() {
  const [toast, setToast] = useState<{ text: string; serial: number } | null>(null);
  const serial = useRef(0);

  const show = useCallback((text: string) => {
    serial.current += 1;
    setToast({ text, serial: serial.current });
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, [toast]);

  return { message: toast?.text ?? null, serial: toast?.serial ?? 0, show };
}

/**
 * A região é montada sempre, com ou sem mensagem: leitor de tela anuncia
 * mudança dentro de uma live region que já existe — criada junto com o texto,
 * ele não anuncia nada.
 *
 * O número de série vira a `key` porque anunciar depende de mutação no DOM, e
 * não do estado do React: reaproveitar o mesmo nó com o mesmo texto não muda
 * nada dentro da região, e o segundo aviso passaria calado justo para quem só
 * tem ele. Trocando a key, o nó é substituído e a mutação existe.
 */
export function Toast({ message, serial }: { message: string | null; serial: number }) {
  return (
    <div role="status" aria-live="polite">
      {message && (
        <span
          key={serial}
          className="inline-block rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-800"
        >
          {message}
        </span>
      )}
    </div>
  );
}
