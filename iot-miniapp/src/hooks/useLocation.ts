/**
 * 定位 Hook
 * <p>
 * 封装 uni.getLocation API，获取用户当前位置，
 * 用于充电站距离排序。
 * </p>
 */
export function useLocation() {
  /** 获取用户位置 */
  function getLocation(): Promise<{ latitude: number; longitude: number }> {
    return new Promise((resolve, reject) => {
      uni.getLocation({
        type: 'gcj02',
        success: (res) => {
          resolve({ latitude: res.latitude, longitude: res.longitude })
        },
        fail: (err) => {
          if (err.errMsg.includes('auth deny')) {
            uni.showModal({
              title: '需要位置权限',
              content: '开启位置权限后可查看附近充电站，是否去设置？',
              success: (modalRes) => {
                if (modalRes.confirm) {
                  uni.openSetting({})
                }
              }
            })
          }
          reject(err)
        }
      })
    })
  }

  return { getLocation }
}
