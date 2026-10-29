import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 3000,
    // 产物目录改用 /static，避免与前端路由 /assets（资产中心）冲突：
    // 默认的 /assets/ 目录会让 nginx 对 GET /assets 返回 301，导致资产页面打不开
    assetsDir: 'static',
  },
})
