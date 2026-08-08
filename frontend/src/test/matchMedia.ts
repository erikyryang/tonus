const DARK_QUERY = '(prefers-color-scheme: dark)';

type ChangeListener = (event: MediaQueryListEvent) => void;

export interface MatchMediaControl {
  /** Simula o usuário trocando o tema do sistema operacional com a aba aberta. */
  setPrefersDark(prefersDark: boolean): void;
}

/**
 * jsdom não implementa `matchMedia`, e o ThemeProvider depende dela para
 * resolver o tema `system`. O stub é controlável de propósito: "mudar o tema do
 * SO troca o tema na hora" só se testa disparando o evento de mudança, não
 * remontando a árvore — remontar testaria a leitura inicial de novo.
 */
export function installMatchMedia(prefersDark = false): MatchMediaControl {
  let dark = prefersDark;
  const listeners = new Set<ChangeListener>();

  window.matchMedia = ((query: string) =>
    ({
      // Getter, não valor fixo: quem guardou a MediaQueryList precisa ver a
      // preferência atual, e não a de quando chamou matchMedia.
      get matches() {
        return query === DARK_QUERY ? dark : false;
      },
      media: query,
      onchange: null,
      addEventListener: (type: string, listener: ChangeListener) => {
        if (type === 'change' && query === DARK_QUERY) listeners.add(listener);
      },
      removeEventListener: (type: string, listener: ChangeListener) => {
        if (type === 'change') listeners.delete(listener);
      },
      dispatchEvent: () => false,
    }) as unknown as MediaQueryList) as typeof window.matchMedia;

  return {
    setPrefersDark(value: boolean) {
      dark = value;
      for (const listener of listeners) {
        listener({ matches: dark, media: DARK_QUERY } as MediaQueryListEvent);
      }
    },
  };
}
