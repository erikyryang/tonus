import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');

  return {
    plugins: [react(), tailwindcss()],
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
