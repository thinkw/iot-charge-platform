import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// Vite 配置：Vue3 + Element Plus + 代理到后端
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    // 代理配置：/api 转发到后端 8080，WebSocket 直连不经代理
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    // 打包输出到后端 static 目录，便于单体部署
    outDir: '../iot-api/src/main/resources/static',
    emptyOutDir: true
  }
})
