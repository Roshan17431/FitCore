import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  root: fileURLToPath(new URL('.', import.meta.url)),
  publicDir: false,
  envDir: false,
  plugins: [react(), tailwindcss()],
  build: {
    outDir: fileURLToPath(new URL('../../output/portfolio/site', import.meta.url)),
    emptyOutDir: true,
  },
});
