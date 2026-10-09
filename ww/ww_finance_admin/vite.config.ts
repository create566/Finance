import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
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
    port: 5173,
    open: true,
    proxy: {
      // 统一入口（2026-10-08 改）：全部走 finance-gateway(8088)。
      // /admin/core/** → service-core；/api/core/** → service-core；
      // /api/oss/file/** → service-oss；/ai/** → 8081。
      // 排障需要绕过网关时用 VITE_API_TARGET / VITE_AI_TARGET 覆盖。
      // 后台管理端 API：/admin/core/**
      '/admin': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8088',
        changeOrigin: true,
      },
      // 前台用户端 API：/api/core/**
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8088',
        changeOrigin: true,
      },
      // AI模块 API：/ai/**
      '/ai': {
        target: process.env.VITE_AI_TARGET || 'http://localhost:8088',
        changeOrigin: true,
      },
    },
  },
})