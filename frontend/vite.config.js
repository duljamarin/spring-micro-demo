import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Everything under /api goes to the gateway, never straight to a service.
    // Going through the proxy means the browser makes same-origin requests, so
    // the app works even if the gateway's CORS config is wrong or absent - and
    // it keeps the front-end honest about the fact that the only supported
    // entry point to this system is :8080.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // A review submission blocks on local CPU inference. Node's proxy
        // default would give up long before the model does, so this has to
        // outlast the gateway's own 70s TimeLimiter.
        timeout: 90000,
        proxyTimeout: 90000,
      },
    },
  },
})
