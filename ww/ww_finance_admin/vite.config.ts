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
      // 后台管理端 API：/admin/core/**
      '/admin': {
        target: 'http://localhost:8990',
        changeOrigin: true,
      },
      // 前台用户端 API：/api/core/**
      '/api': {
        target: 'http://localhost:8990',
        changeOrigin: true,
      },
      // AI模块 API：/ai/**
      '/ai': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})