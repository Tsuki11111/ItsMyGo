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
    // PORT 优先 —— 让预览 / 测试工具能挑空闲端口，避免和已在跑的 dev server 撞车。
    // 平时 `npm run dev` 没设 PORT，仍然是 5173。
    port: Number(process.env.PORT) || 5173,
    // 开发时把 /api 代理到后端 —— 这样浏览器看到的是同源请求，
    // 后端一行 CORS 配置都不用加。生产环境用 Nginx 做同样的事。
    proxy: {
      '/api': {
        // 允许指向另一个后端实例（例如 8080 被别的会话占着时，用 API_TARGET 指到 8081 验证）
        target: process.env.API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
