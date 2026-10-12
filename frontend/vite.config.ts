import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const backend = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080'

/**
 * The storefront talks to the Spring Boot API through the dev server so that the
 * browser sees a single origin and no CORS preflight is needed during development.
 *
 * `allowedHosts` is widened because the app is also served through a generated
 * sandbox hostname; Vite rejects unknown Host headers by default. Set
 * SHOPWISE_ALLOWED_HOSTS to a comma-separated list to restrict it in a real deployment.
 */
const allowedHosts = process.env.SHOPWISE_ALLOWED_HOSTS
  ? process.env.SHOPWISE_ALLOWED_HOSTS.split(',').map((host) => host.trim())
  : true

const proxy = {
  '/api': { target: backend, changeOrigin: true },
  '/actuator': { target: backend, changeOrigin: true },
}

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: false,
    allowedHosts,
    proxy,
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
    allowedHosts,
    proxy,
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
})
