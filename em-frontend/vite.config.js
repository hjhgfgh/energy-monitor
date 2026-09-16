import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 生产环境由网关同源托管（见 em-gateway 静态资源），开发环境用 proxy 转发到网关
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8090', changeOrigin: true },
      '/ws':   { target: 'ws://127.0.0.1:8090', ws: true }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500
  }
})
