// IoT 充电平台 - 微信小程序端 Vite 配置
// 关键增强：
//   1) 注入环境变量，使 src/manifest.json 中的 __WECHAT_APPID__ 占位
//      在构建时被替换为 VITE_WECHAT_APPID。
//   2) 处理微信 AppID 这种非源码的"配置型常量"集中管理。
//
// 策略（使用 buildStart 写回磁盘而非 transform 钩子）：
//   - uni 插件直接读 manifest.json 磁盘内容，不经过 Vite transform 链路
//   - buildStart 时机在 uni 读取之前，确保插件拿到的内容已替换
//   - 本地无 manifest.json 时自动从 manifest.json.example 模板复制
//   - manifest.json 已被 .gitignore 排除，不入仓

import { defineConfig, loadEnv } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import { fileURLToPath, URL } from 'node:url'
import fs from 'node:fs'
import path from 'node:path'

const MANIFEST_PATH = fileURLToPath(new URL('./src/manifest.json', import.meta.url))
const MANIFEST_EXAMPLE_PATH = fileURLToPath(new URL('./src/manifest.json.example', import.meta.url))
const PLACEHOLDER = '__WECHAT_APPID__'

function ensureManifest() {
  if (fs.existsSync(MANIFEST_PATH)) return
  if (!fs.existsSync(MANIFEST_EXAMPLE_PATH)) {
    throw new Error(
      `[iot-miniapp] 缺少 ${MANIFEST_PATH}，且模板 ${MANIFEST_EXAMPLE_PATH} 也不存在。` +
      `请创建 src/manifest.json（参考 src/manifest.json.example）。`
    )
  }
  fs.copyFileSync(MANIFEST_EXAMPLE_PATH, MANIFEST_PATH)
  console.log(`[iot-miniapp] 已从 manifest.json.example 初始化 manifest.json`)
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const wechatAppId = env.VITE_WECHAT_APPID || ''

  return {
    plugins: [
      uni(),
      {
        // 在构建启动时直接改写 src/manifest.json 磁盘文件，
        // 因为 manifest.json 不走 Vite transform 链路（uni 插件直读磁盘）。
        name: 'iot-miniapp-manifest-inject',
        enforce: 'pre',
        apply: 'build',
        buildStart() {
          try {
            ensureManifest()
            const raw = fs.readFileSync(MANIFEST_PATH, 'utf-8')
            if (!raw.includes(PLACEHOLDER)) return // 已替换过，不动
            if (!wechatAppId) {
              console.warn(
                `[iot-miniapp] ${path.basename(MANIFEST_PATH)} 含占位符 ${PLACEHOLDER}，` +
                `但未配置 VITE_WECHAT_APPID，构建出的 manifest.appid 将保持占位符。`
              )
              return
            }
            const replaced = raw.split(PLACEHOLDER).join(wechatAppId)
            fs.writeFileSync(MANIFEST_PATH, replaced, 'utf-8')
            console.log(
              `[iot-miniapp] 已将 ${path.basename(MANIFEST_PATH)} 中的 ` +
              `${PLACEHOLDER} 替换为 ${wechatAppId}`
            )
          } catch (e) {
            console.error('[iot-miniapp] 注入 AppID 失败:', e)
          }
        }
      }
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    }
  }
})
