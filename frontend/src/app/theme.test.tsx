import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { installMatchMedia } from '@/test/matchMedia';
import { ThemeToggle } from './ThemeToggle';
import { ThemeProvider, useTheme } from './theme';

const STORAGE_KEY = 'tonus-theme';

const isDark = () => document.documentElement.classList.contains('dark');

function ThemeProbe() {
  const { theme, resolvedTheme } = useTheme();
  return (
    <>
      <span data-testid="theme">{theme}</span>
      <span data-testid="resolved">{resolvedTheme}</span>
    </>
  );
}

function renderTheme() {
  return render(
    <ThemeProvider>
      <ThemeProbe />
      <ThemeToggle />
    </ThemeProvider>,
  );
}

describe('tema', () => {
  it('sem preferência salva, segue o sistema escuro', () => {
    installMatchMedia(true);

    renderTheme();

    expect(isDark()).toBe(true);
    expect(screen.getByTestId('theme')).toHaveTextContent('system');
    expect(screen.getByTestId('resolved')).toHaveTextContent('dark');
  });

  it('sem preferência salva, segue o sistema claro', () => {
    installMatchMedia(false);

    renderTheme();

    expect(isDark()).toBe(false);
    expect(screen.getByTestId('resolved')).toHaveTextContent('light');
  });

  it('preferência salva vence a do sistema no carregamento', () => {
    installMatchMedia(true);
    window.localStorage.setItem(STORAGE_KEY, 'light');

    renderTheme();

    expect(isDark()).toBe(false);
    expect(screen.getByTestId('theme')).toHaveTextContent('light');
  });

  it('escolher escuro grava a preferência e aplica a classe no html', async () => {
    const user = userEvent.setup();
    installMatchMedia(false);
    renderTheme();

    await user.click(screen.getByRole('button', { name: 'Escuro' }));

    expect(isDark()).toBe(true);
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('dark');
    expect(screen.getByRole('button', { name: 'Escuro' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('em system, mudar o tema do SO troca o tema sem reload', () => {
    const media = installMatchMedia(false);
    renderTheme();
    expect(isDark()).toBe(false);

    act(() => media.setPrefersDark(true));

    expect(isDark()).toBe(true);
    expect(screen.getByTestId('resolved')).toHaveTextContent('dark');
  });

  it('não segue mais o SO depois que o usuário escolheu um tema', async () => {
    const user = userEvent.setup();
    const media = installMatchMedia(false);
    renderTheme();

    await user.click(screen.getByRole('button', { name: 'Claro' }));
    act(() => media.setPrefersDark(true));

    expect(isDark()).toBe(false);
  });

  it('valor inválido no localStorage não quebra a aplicação: cai em system', () => {
    installMatchMedia(true);
    window.localStorage.setItem(STORAGE_KEY, 'roxo-neon');

    renderTheme();

    expect(screen.getByTestId('theme')).toHaveTextContent('system');
    expect(isDark()).toBe(true);
  });

  it('voltar para system devolve o controle ao sistema', async () => {
    const user = userEvent.setup();
    const media = installMatchMedia(false);
    window.localStorage.setItem(STORAGE_KEY, 'dark');
    renderTheme();
    expect(isDark()).toBe(true);

    await user.click(screen.getByRole('button', { name: 'Sistema' }));

    expect(isDark()).toBe(false);
    act(() => media.setPrefersDark(true));
    expect(isDark()).toBe(true);
  });
});
