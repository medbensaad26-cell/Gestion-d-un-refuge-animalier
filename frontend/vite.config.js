import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The Java server remains the only backend. Requests keep their /api prefix.
const apiProxy = {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
}

export default defineConfig({
  plugins: [react()],
  server: {
    host: 'localhost',
    port: 5173,
    strictPort: true,
    proxy: apiProxy,
  },
  preview: {
    host: 'localhost',
    port: 4173,
    strictPort: true,
    proxy: apiProxy,
  },
})
