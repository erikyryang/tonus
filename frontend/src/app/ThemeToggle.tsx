import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';
import { type Theme, useTheme } from './theme';

const ICON_PROPS = {
  width: 14,
  height: 14,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
} as const;

const SunIcon = () => (
  <svg {...ICON_PROPS}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
  </svg>
);

const MoonIcon = () => (
  <svg {...ICON_PROPS}>
    <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
  </svg>
);

const MonitorIcon = () => (
  <svg {...ICON_PROPS}>
    <rect x="2" y="3" width="20" height="14" rx="2" />
    <path d="M8 21h8M12 17v4" />
  </svg>
);

/** Ícone sozinho não diz qual estado está ativo — sempre com rótulo textual. */
const OPTIONS: { value: Theme; label: string; icon: ReactNode }[] = [
  { value: 'light', label: 'Claro', icon: <SunIcon /> },
  { value: 'dark', label: 'Escuro', icon: <MoonIcon /> },
  { value: 'system', label: 'Sistema', icon: <MonitorIcon /> },
];

/**
 * `vertical` existe por causa da sidebar do ADMIN: os três rótulos lado a lado
 * pedem ~240px e lá só há 192px, e como o rótulo é textual por exigência da
 * spec, não dá para encolher o conteúdo — em linha, "Sistema" vaza para fora.
 */
export function ThemeToggle({
  className,
  orientation = 'horizontal',
}: {
  className?: string;
  orientation?: 'horizontal' | 'vertical';
}) {
  const { theme, setTheme } = useTheme();
  const isVertical = orientation === 'vertical';

  return (
    <div
      role="group"
      aria-label="Tema"
      className={cn('flex gap-0.5 rounded-md border border-border p-0.5', isVertical && 'flex-col', className)}
    >
      {OPTIONS.map((option) => {
        const isActive = theme === option.value;
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={isActive}
            onClick={() => setTheme(option.value)}
            className={cn(
              'flex items-center gap-1 rounded px-2 py-1 text-xs transition-colors',
              isVertical ? 'justify-start' : 'flex-1 justify-center',
              isActive ? 'bg-accent font-medium text-accent-foreground' : 'text-muted-foreground hover:bg-accent/50',
            )}
          >
            {option.icon}
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
