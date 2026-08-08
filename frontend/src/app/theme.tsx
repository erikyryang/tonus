import { type ReactNode, createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useState } from 'react';

export type Theme = 'light' | 'dark' | 'system';
/** O que está valendo de fato — `system` já resolvido. */
export type ResolvedTheme = 'light' | 'dark';

/**
 * Chave e classe combinam com o script inline do index.html: mudou aqui, muda
 * lá. Tema não é credencial, então localStorage não fere a regra de auth do
 * CLAUDE.md — e é preferência de dispositivo, não de pessoa, por isso não vai
 * para o backend.
 */
export const THEME_STORAGE_KEY = 'tonus-theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';

function isTheme(value: string | null): value is Theme {
  return value === 'light' || value === 'dark' || value === 'system';
}

/** Valor estranho no storage cai em `system` em vez de derrubar a aplicação. */
function readStoredTheme(): Theme {
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isTheme(stored) ? stored : 'system';
  } catch {
    // Storage bloqueado (aba anônima, cookies desligados): seguir o sistema.
    return 'system';
  }
}

function systemPrefersDark(): boolean {
  return window.matchMedia(DARK_QUERY).matches;
}

interface ThemeContextValue {
  theme: Theme;
  resolvedTheme: ResolvedTheme;
  setTheme: (theme: Theme) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(readStoredTheme);
  const [prefersDark, setPrefersDark] = useState<boolean>(systemPrefersDark);

  /**
   * Sem o listener, `system` mentiria até o próximo reload: o usuário troca o
   * tema do SO e a aba continua no anterior.
   */
  useEffect(() => {
    const query = window.matchMedia(DARK_QUERY);
    const onChange = (event: MediaQueryListEvent) => setPrefersDark(event.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);

  const resolvedTheme: ResolvedTheme = theme === 'system' ? (prefersDark ? 'dark' : 'light') : theme;

  /**
   * Na montagem isto não reaplica nada: o script inline do index.html leu as
   * mesmas duas entradas (storage e media query) e já deixou a classe certa no
   * <html> — o toggle com o mesmo valor é no-op. O efeito existe para as
   * mudanças posteriores (escolha do usuário, tema do SO).
   */
  useLayoutEffect(() => {
    document.documentElement.classList.toggle('dark', resolvedTheme === 'dark');
  }, [resolvedTheme]);

  const setTheme = useCallback((next: Theme) => {
    setThemeState(next);
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch {
      // Sem persistência a escolha vale só para esta aba — melhor que quebrar.
    }
  }, []);

  const value = useMemo(() => ({ theme, resolvedTheme, setTheme }), [theme, resolvedTheme, setTheme]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme precisa estar dentro do ThemeProvider');
  return context;
}
