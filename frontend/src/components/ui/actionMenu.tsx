import { useEffect, useRef, useState } from 'react';
import { Button } from './button';

export interface MenuAction {
  label: string;
  onSelect: () => void;
}

/**
 * Menu de linha. Também à mão, pelo mesmo motivo do Dialog: um popover de três
 * itens não justifica uma dependência de UI nova.
 *
 * Ao escolher um item, o foco volta para o botão ANTES de a ação rodar. É o que
 * permite ao diálogo aberto pela ação saber para onde devolver o foco depois —
 * o item de menu que foi clicado já não existe quando o diálogo fecha.
 */
export function ActionMenu({ label, actions }: { label: string; actions: MenuAction[] }) {
  const [open, setOpen] = useState(false);
  const container = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    menu.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onDocumentDown = (event: MouseEvent) => {
      if (!container.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDocumentDown);
    return () => document.removeEventListener('mousedown', onDocumentDown);
  }, [open]);

  function close({ restoreFocus }: { restoreFocus: boolean }) {
    if (restoreFocus) trigger.current?.focus();
    setOpen(false);
  }

  function onMenuKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'Escape') {
      event.preventDefault();
      close({ restoreFocus: true });
      return;
    }
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;

    const items = [...(menu.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])];
    if (items.length === 0) return;

    event.preventDefault();
    const current = items.indexOf(document.activeElement as HTMLElement);
    const next: Record<string, number> = {
      ArrowDown: (current + 1) % items.length,
      ArrowUp: (current - 1 + items.length) % items.length,
      Home: 0,
      End: items.length - 1,
    };
    items[next[event.key]!]?.focus();
  }

  return (
    <div ref={container} className="relative">
      <Button
        ref={trigger}
        variant="ghost"
        size="sm"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        ⋯
      </Button>

      {open && (
        <div
          ref={menu}
          role="menu"
          aria-label={label}
          onKeyDown={onMenuKeyDown}
          className="absolute right-0 z-10 mt-1 min-w-36 rounded-md border border-border bg-popover text-popover-foreground py-1 shadow-md"
        >
          {actions.map((action) => (
            <button
              key={action.label}
              type="button"
              role="menuitem"
              className="block w-full px-3 py-1.5 text-left text-sm text-foreground hover:bg-accent focus:bg-accent focus:outline-none"
              onClick={() => {
                close({ restoreFocus: true });
                action.onSelect();
              }}
            >
              {action.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
