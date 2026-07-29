import { type ReactNode, useEffect, useId, useRef } from 'react';

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])';

/**
 * Modal escrito à mão, e não shadcn/Radix: o projeto não adota biblioteca de UI
 * nova sem discussão, e o que a agenda precisa é confirmação simples.
 * Deliberadamente sem <dialog> nativo — o suporte no jsdom é irregular e o
 * comportamento passaria a depender do ambiente de teste.
 *
 * O que uma biblioteca daria de graça e aqui é explícito: o foco entra no
 * diálogo, fica preso nele enquanto está aberto e volta para quem o abriu ao
 * fechar. Sem isso, quem navega por teclado continuaria tabulando pela agenda
 * atrás do modal e perderia a posição ao fechá-lo.
 */
export function Dialog({
  title,
  onClose,
  busy = false,
  children,
}: {
  title: string;
  onClose: () => void;
  /** Mutação em andamento: fechar por Escape ou clique no fundo fica bloqueado. */
  busy?: boolean;
  children: ReactNode;
}) {
  const titleId = useId();
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    focusFirst(panel.current);
    return () => {
      // Elemento pode ter saído do DOM junto com o menu que abriu o diálogo.
      if (opener?.isConnected) opener.focus();
    };
  }, []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) {
        onClose();
        return;
      }
      if (event.key === 'Tab') trapTab(event, panel.current);
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [busy, onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) onClose();
      }}
    >
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="w-full max-w-md space-y-4 rounded-lg bg-white p-6 shadow-lg focus:outline-none"
      >
        <h2 id={titleId} className="text-lg font-semibold text-slate-900">
          {title}
        </h2>
        {children}
      </div>
    </div>
  );
}

export function DialogActions({ children }: { children: ReactNode }) {
  return <div className="flex justify-end gap-2 pt-2">{children}</div>;
}

/**
 * O primeiro foco é o primeiro campo — ou, nas confirmações, o "Voltar". Sair
 * com o cursor sobre a ação destrutiva convidaria a confirmar no Enter.
 */
function focusFirst(panel: HTMLElement | null) {
  const target = panel?.querySelector<HTMLElement>(FOCUSABLE);
  (target ?? panel)?.focus();
}

function trapTab(event: KeyboardEvent, panel: HTMLElement | null) {
  const focusable = [...(panel?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])];
  if (focusable.length === 0) return;

  const first = focusable[0]!;
  const last = focusable[focusable.length - 1]!;
  const active = document.activeElement;

  if (event.shiftKey && (active === first || active === panel)) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && active === last) {
    event.preventDefault();
    first.focus();
  }
}
