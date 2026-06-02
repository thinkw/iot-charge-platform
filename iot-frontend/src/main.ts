import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 状态管理
app.use(createPinia())
// 路由
app.use(router)
// Element Plus UI 组件库
app.use(ElementPlus, { size: 'default' })

app.mount('#app')
