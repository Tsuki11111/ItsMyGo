/**
 * 腾讯地图 JavaScript API GL 的公共加载器。
 *
 * 物流地图和发货地址弹窗都要用，各写一份会重复插入 script。
 * Key 只从 VITE_TMAP_KEY 读，不进代码。
 */

declare global {
  interface Window {
    TMap?: any
  }
}

let loading: Promise<any> | null = null

export function loadTMap(): Promise<any> {
  if (window.TMap) return Promise.resolve(window.TMap)
  if (loading) return loading

  const env = import.meta.env as Record<string, string | undefined>
  const key = env.VITE_TMAP_KEY
  if (!key) {
    return Promise.reject(new Error('未配置腾讯地图 Key'))
  }

  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    // libraries=service 才会挂载 TMap.service（地理编码 / 搜索等）
    script.src = `https://map.qq.com/api/gljs?v=1.exp&libraries=service&key=${encodeURIComponent(key)}`
    script.async = true
    script.onload = () => resolve(window.TMap)
    script.onerror = () => {
      loading = null
      reject(new Error('腾讯地图脚本加载失败'))
    }
    document.head.appendChild(script)
  })
  return loading
}

export interface GeoPoint {
  lng: number
  lat: number
}

/** 把中文地址解析成腾讯地图坐标（GCJ-02） */
export async function geocodeAddress(address: string): Promise<GeoPoint | null> {
  const tmap = await loadTMap()
  if (!tmap.service?.Geocoder) {
    throw new Error('腾讯地图 Geocoder 不可用')
  }
  const geocoder = new tmap.service.Geocoder()
  const result = await geocoder.getLocation({ address })
  const location = result?.result?.location || result?.location
  if (!location || location.lng == null || location.lat == null) {
    return null
  }
  return { lng: Number(location.lng), lat: Number(location.lat) }
}
