import { useEffect, useState } from 'react';

const TIMEOUT_MS = 4000;

/** Confirmação efêmera do que acabou de acontecer. */
export function useToast() {
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => setMessage(null), TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, [message]);

  return { message, show: setMessage };
}

/**
 * A região é montada sempre, com ou sem mensagem: leitor de tela anuncia
 * mudança dentro de uma live region que já existe — criada junto com o texto,
 * ele não anuncia nada.
 */
export function Toast({ message }: { message: string | null }) {
  return (
    <div role="status" aria-live="polite">
      {message && (
        <span className="inline-block rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{message}</span>
      )}
    </div>
  );
}
