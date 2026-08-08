/**
 * No Node 26 o `localStorage` experimental do próprio Node ocupa a propriedade
 * no window do jsdom e devolve `undefined` sem a flag `--localstorage-file`.
 * Um Storage em memória por teste resolve e ainda dá isolamento: cada teste
 * começa com o storage vazio, sem depender de limpeza no final do anterior.
 *
 * É stub de API do browser na fronteira, não mock de módulo nosso — o
 * ThemeProvider continua falando com `window.localStorage` de verdade.
 */
export function installLocalStorage(): void {
  const store = new Map<string, string>();

  const storage: Storage = {
    get length() {
      return store.size;
    },
    clear: () => store.clear(),
    getItem: (key: string) => store.get(key) ?? null,
    key: (index: number) => [...store.keys()][index] ?? null,
    removeItem: (key: string) => void store.delete(key),
    setItem: (key: string, value: string) => void store.set(key, String(value)),
  };

  Object.defineProperty(window, 'localStorage', { value: storage, configurable: true, writable: true });
}
