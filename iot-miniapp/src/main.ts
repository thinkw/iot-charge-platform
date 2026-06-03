import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'

/**
 * uni-app 应用入口
 * <p>
 * 使用 createSSRApp 以兼容小程序 SSR 渲染模式。
 * Pinia 状态管理全局注册。
 * </p>
 */
export function createApp() {
  const app = createSSRApp(App)
  app.use(createPinia())
  return { app }
}
