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
      // 默认打本地 8990；自测时可用 VITE_API_TARGET 指向别的实例（如 8991），
      // 避免为了自测去改这个文件、又忘了改回来。
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8990',
        changeOrigin: true,
      },
      '/ai': {
        target: process.env.VITE_AI_TARGET || 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})