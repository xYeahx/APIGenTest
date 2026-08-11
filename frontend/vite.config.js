import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发环境代理：/api 转发到后端
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})