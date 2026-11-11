import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * 局域网 HTTPS 证书（可选）
 *
 * 浏览器要求 GPS 定位必须在安全上下文（HTTPS 或 localhost）下，
 * 局域网 HTTP 访问时 navigator.geolocation 会被拒绝。
 * 若 certs/ 下存在证书则自动启用 HTTPS。
 */
const certDir = resolve(process.cwd(), 'certs')
const certFile = resolve(certDir, 'cert.pem')
const keyFile = resolve(certDir, 'key.pem')
const httpsConfig =
  existsSync(certFile) && existsSync(keyFile)
    ? { cert: readFileSync(certFile), key: readFileSync(keyFile) }
    : undefined

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
    // 有证书时启用 HTTPS，让局域网手机也能用 GPS 定位
    https: httpsConfig,
    // 开发时把 /smtchs 转发到后端，避免跨域
    proxy: {
      '/smtchs': {
        target: process.env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
