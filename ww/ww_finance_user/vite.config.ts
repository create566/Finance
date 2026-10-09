import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    chunkSizeWarningLimit: 1000,
    rolldownOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('element-plus') || id.includes('@element-plus/icons-vue')) {
              return 'element-plus'
            }
            if (id.includes('vue') || id.includes('pinia')) {
              return 'vue-vendor'
            }
          }
        },
      },
    },
  },
  server: {
    port: 5174,
    // 默认自动开浏览器；VITE_OPEN=false 可关闭（无人值守/自测时用）
    open: process.env.VITE_OPEN !== 'false',
    proxy: {
      // 统一入口（2026-10-08 改）：前端只跟 finance-gateway(8088) 说话，
      // 由网关按路径转发：/api/user/** + /*/core/** → service-core、
      // /api/oss/file/** → service-oss、/api/sms/** → service-sms、/ai/** → 8081。
      // 这样「网关」才真正被使用；否则它只是一条没人走的路。
      // 排障/自测需要绕过网关直连后端时，用环境变量覆盖即可（如
      // VITE_API_TARGET=http://localhost:8991 起一个隔离实例）。
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8088',
        changeOrigin: true,
      },
      '/ai': {
        target: process.env.VITE_AI_TARGET || 'http://localhost:8088',
        changeOrigin: true,
      },
    },
  },
})