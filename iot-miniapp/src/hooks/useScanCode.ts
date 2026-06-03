/**
 * 扫码 Hook
 * <p>
 * 封装 uni.scanCode API，用于扫描充电桩二维码获取 chargerId。
 * </p>
 */
export function useScanCode() {
  function scan(): Promise<number> {
    return new Promise((resolve, reject) => {
      uni.scanCode({
        success: (res) => {
          // 扫码结果格式：chargerId=123 或直接数字
          const raw = res.result
          const match = raw.match(/chargerId[=:]?\s*(\d+)/i)
          const id = match ? parseInt(match[1]) : parseInt(raw)
          if (isNaN(id)) {
            uni.showToast({ title: '无效的二维码', icon: 'none' })
            reject(new Error('无效的二维码'))
            return
          }
          resolve(id)
        },
        fail: (err) => {
          // 用户取消扫码
          if (err.errMsg.includes('cancel')) {
            reject(new Error('用户取消扫码'))
            return
          }
          uni.showToast({ title: '扫码失败', icon: 'none' })
          reject(err)
        }
      })
    })
  }

  return { scan }
}
