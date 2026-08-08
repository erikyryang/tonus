import { createHash } from 'node:crypto';
import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { loadEnv, type Plugin } from 'vite';
import { defineConfig } from 'vitest/config';

/**
 * CSP only in the build: in dev, Vite's HMR client runs under different
 * rules (module served over WebSocket, without going through the final
 * index.html), so `apply: 'build'` avoids breaking `npm run dev`.
 * Directives the CSP spec ignores in <meta> (frame-ancestors) and the other
 * security headers (X-Frame-Options, Referrer-Policy) are handled by Caddy
 * in production — see docs/design-summary.md.
 */
/**
 * O script que aplica o tema antes do bundle precisa ser inline (senão pisca
 * tema errado) e `script-src 'self'` bloqueia inline. Liberar por hash mantém
 * a política estrita: só passa exatamente este conteúdo, e editá-lo sem
 * rebuildar invalida o hash em vez de abrir a porta para qualquer inline.
 */
function inlineScriptHashes(html: string): string[] {
  const inlineScript = /<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi;
  return [...html.matchAll(inlineScript)].map(
    ([, content]) => `'sha256-${createHash('sha256').update(content, 'utf8').digest('base64')}'`,
  );
}

function cspMetaTag(): Plugin {
  const policy = (html: string) => [
    "default-src 'self'",
    ["script-src 'self'", ...inlineScriptHashes(html)].join(' '),
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
        `<meta charset="UTF-8" />\n    <meta http-equiv="Content-Security-Policy" content="${policy(html)}" />`,
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
         * Frontend and API are same-origin by architectural decision: here
         * in dev it's this proxy, in production it's Caddy. That's what
         * allows an httpOnly cookie with no CORS in the backend. The /api
         * prefix only exists on the browser side — the backend serves
         * /auth, /students, etc. at the root.
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
