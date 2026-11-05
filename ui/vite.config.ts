import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react(), tailwindcss()],
    server: {
      proxy: {
        '/api/auth': {
          target: env.VITE_AUTH_PROXY_TARGET || 'http://localhost:8090',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, '') || '/',
        },
        '/api': {
          target: env.VITE_API_PROXY_TARGET || 'http://localhost:8081',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, '') || '/',
        },
      },
    },
  }
})
