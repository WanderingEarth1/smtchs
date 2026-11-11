import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 0.0.0.0 允许同一局域网内的手机/电脑访问，便于演示
    host: '0.0.0.0',
    port: 5273,
    strictPort: false,
    // 开发时把 /smtchs 转发到后端，避免跨域
    proxy: {
      '/smtchs': {
        target: process.env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
