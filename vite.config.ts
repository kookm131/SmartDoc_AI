import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');

  return {
    plugins: [react(), tailwindcss()],
    define: {
      'process.env.GEMINI_API_KEY': JSON.stringify(env.GEMINI_API_KEY),
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    server: {
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modify-file watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
      proxy: {
        '/api/document': {
          target: env.VITE_DOCUMENT_API_TARGET || 'http://localhost:8081',
          changeOrigin: true,
          rewrite: (apiPath) => apiPath.replace(/^\/api\/document/, '/api/v1'),
        },
        '/api/analysis': {
          target: env.VITE_ANALYSIS_API_TARGET || 'http://localhost:8082',
          changeOrigin: true,
          rewrite: (apiPath) => apiPath.replace(/^\/api\/analysis/, '/api/v1'),
        },
      },
    },
  };
});
