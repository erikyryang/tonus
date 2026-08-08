import { type ReactNode, useEffect, useId, useRef } from 'react';

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])';

/**
 * Modal written by hand, and not shadcn/Radix: the project doesn't adopt a new UI
 * library without discussion, and what the schedule needs is simple confirmation.
 * Deliberately without native <dialog> — support in jsdom is inconsistent and
 * behavior would end up depending on the test environment.
 *
 * What a library would give for free and here is explicit: focus enters the
 * dialog, stays trapped in it while open and returns to whoever opened it on
 * close. Without this, whoever navigates by keyboard would keep tabbing through the schedule
 * behind the modal and would lose their position when closing it.
 */
export function Dialog({
  title,
  onClose,
  busy = false,
  children,
}: {
  title: string;
  onClose: () => void;
  /** Mutation in progress: closing via Escape or clicking the backdrop is blocked. */
  busy?: boolean;
  children: ReactNode;
}) {
  const titleId = useId();
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    focusFirst(panel.current);
    return () => {
      // Element may have left the DOM along with the menu that opened the dialog.
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
      className="fixed inset-0 z-50 flex items-center justify-center bg-overlay p-4"
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
        className="w-full max-w-md space-y-4 rounded-lg bg-popover text-popover-foreground p-6 shadow-lg focus:outline-none"
      >
        <h2 id={titleId} className="text-lg font-semibold text-foreground">
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
 * The first focus is the first field — or, in confirmations, "Back". Leaving
 * the cursor over the destructive action would invite confirming with Enter.
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
