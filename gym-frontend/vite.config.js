import { defineConfig } from 'vite'
import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: [
      {
        find: /^lucide-react$/,
        replacement: fileURLToPath(new URL('./src/icons/lucide.js', import.meta.url)),
      },
      {
        find: /^recharts$/,
        replacement: fileURLToPath(new URL('./src/charts/recharts.js', import.meta.url)),
      },
    ],
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
  },
  optimizeDeps: {
    include: [
      'cookie',
      'react',
      'react-dom',
      'react-dom/client',
      'react/jsx-dev-runtime',
      'react/jsx-runtime',
      'set-cookie-parser',
    ],
  },
})
