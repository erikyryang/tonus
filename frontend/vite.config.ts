import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { loadEnv, type Plugin } from 'vite';
import { defineConfig } from 'vitest/config';

/**
 * CSP só no build: em dev o cliente de HMR do Vite roda sob outras regras
 * (módulo servido via WebSocket, sem passar pelo index.html final), então
 * `apply: 'build'` evita travar `npm run dev`. Diretivas que a spec de CSP
 * ignora em <meta> (frame-ancestors) e os demais security headers (X-Frame-
 * Options, Referrer-Policy) ficam por conta do Caddy em produção — ver
 * docs/design-summary.md.
 */
function cspMetaTag(): Plugin {
  const policy = [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self'",
    "img-src 'self' data:",
    "font-src 'self'",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'self'",
  ].join('; ');

  return {
    name: 'tonus-csp-meta',
    apply: 'build',
    transformIndexHtml(html) {
      return html.replace(
        '<meta charset="UTF-8" />',
        `<meta charset="UTF-8" />\n    <meta http-equiv="Content-Security-Policy" content="${policy}" />`,
      );
    },
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');

  return {
    plugins: [react(), tailwindcss(), cspMetaTag()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: {
        /**
         * Front e API são same-origin por decisão de arquitetura: aqui em dev
         * é este proxy, em produção é o Caddy. É o que permite cookie httpOnly
         * sem CORS no backend. O prefixo /api existe só do lado do navegador —
         * o backend serve /auth, /students, etc. na raiz.
         */
        '/api': {
          target: env.VITE_API_TARGET ?? 'http://localhost:8080',
          changeOrigin: false,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      css: false,
    },
  };
});
