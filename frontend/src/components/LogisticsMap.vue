<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { LogisticsView } from '@/api/types'
import { loadTMap } from '@/utils/tencentMap'

const props = defineProps<{ logistics: LogisticsView }>()

const mapEl = ref<HTMLElement>()
const mapError = ref('')
let tmap: any = null
let map: any = null
let markerLayer: any = null
let lineLayer: any = null
let routePath: any[] = []
let originGeom: any = null
let destGeom: any = null
let loaded = false

/** 地址解析失败时的城市中心兜底（腾讯地图 GCJ-02 坐标） */
const CITY_CENTERS: Array<[string, number, number]> = [
  ['北京', 116.4074, 39.9042],
  ['上海', 121.4737, 31.2304],
  ['广州', 113.2644, 23.1291],
  ['深圳', 114.0579, 22.5431],
  ['杭州', 120.1551, 30.2741],
  ['南京', 118.7969, 32.0603],
  ['成都', 104.0665, 30.5723],
  ['武汉', 114.3055, 30.5928],
  ['西安', 108.9398, 34.3416],
  ['重庆', 106.5516, 29.5630],
  ['天津', 117.2009, 39.0842],
  ['苏州', 120.5853, 31.2989],
  ['郑州', 113.6254, 34.7466],
  ['长沙', 112.9388, 28.2282],
  ['青岛', 120.3826, 36.0671],
  ['厦门', 118.0894, 24.4798],
  ['福州', 119.2965, 26.0745],
  ['合肥', 117.2272, 31.8206],
  ['济南', 117.1201, 36.6512],
  ['沈阳', 123.4315, 41.8057],
  ['大连', 121.6147, 38.9140],
  ['哈尔滨', 126.5349, 45.8038],
  ['长春', 125.3235, 43.8171],
  ['昆明', 102.8329, 24.8801],
  ['贵阳', 106.6302, 26.6470],
  ['南宁', 108.3669, 22.8170],
  ['海口', 110.1983, 20.0440],
  ['兰州', 103.8343, 36.0611],
  ['西宁', 101.7782, 36.6171],
  ['银川', 106.2309, 38.4872],
  ['乌鲁木齐', 87.6168, 43.8256],
  ['呼和浩特', 111.7492, 40.8426],
  ['拉萨', 91.1409, 29.6456],
  ['石家庄', 114.5149, 38.0428],
  ['太原', 112.5489, 37.8706],
  ['南昌', 115.8582, 28.6820],
]

function dotSvg(color: string, size = 24): string {
  return 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
      <circle cx="${size / 2}" cy="${size / 2}" r="${size / 2 - 3}"
        fill="${color}" stroke="#ffffff" stroke-width="3"/>
    </svg>`,
  )
}

function courierSvg(size = 34): string {
  return 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
      <circle cx="${size / 2}" cy="${size / 2}" r="${size / 2 - 4}"
        fill="#d6336c" stroke="#ffffff" stroke-width="4"/>
      <circle cx="${size / 2}" cy="${size / 2}" r="${size / 2 - 13}" fill="#ffffff"/>
    </svg>`,
  )
}

function lngOf(point: any): number {
  return typeof point.getLng === 'function' ? point.getLng() : Number(point.lng)
}

function latOf(point: any): number {
  return typeof point.getLat === 'function' ? point.getLat() : Number(point.lat)
}

function curvePoints(from: any, to: any): any[] {
  const fromLng = lngOf(from)
  const fromLat = latOf(from)
  const toLng = lngOf(to)
  const toLat = latOf(to)
  const midLng = (fromLng + toLng) / 2 + (toLat - fromLat) * 0.08
  const midLat = (fromLat + toLat) / 2 - (toLng - fromLng) * 0.08
  const points: any[] = []
  for (let i = 0; i <= 60; i += 1) {
    const t = i / 60
    const lng = (1 - t) * (1 - t) * fromLng + 2 * (1 - t) * t * midLng + t * t * toLng
    const lat = (1 - t) * (1 - t) * fromLat + 2 * (1 - t) * t * midLat + t * t * toLat
    points.push(new tmap.LatLng(lat, lng))
  }
  return points
}

function positionAt(progress: number): any {
  if (!routePath.length) return null
  const ratio = Math.max(0, Math.min(100, Number(progress) || 0)) / 100
  if (routePath.length === 1) return routePath[0]

  let total = 0
  const distances: number[] = []
  for (let i = 1; i < routePath.length; i += 1) {
    const a = routePath[i - 1]
    const b = routePath[i]
    const d = Math.hypot(lngOf(b) - lngOf(a), latOf(b) - latOf(a))
    distances.push(d)
    total += d
  }
  if (total <= 0) return routePath[0]

  const target = total * ratio
  let walked = 0
  for (let i = 1; i < routePath.length; i += 1) {
    const segment = distances[i - 1]
    if (walked + segment >= target && segment > 0) {
      const part = (target - walked) / segment
      const a = routePath[i - 1]
      const b = routePath[i]
      return new tmap.LatLng(
        latOf(a) + (latOf(b) - latOf(a)) * part,
        lngOf(a) + (lngOf(b) - lngOf(a)) * part,
      )
    }
    walked += segment
  }
  return routePath[routePath.length - 1]
}

function updateMarkers() {
  if (!markerLayer || !originGeom || !destGeom) return
  const courier = positionAt(props.logistics.progress)
  const geometries = [originGeom, destGeom]
  if (courier) {
    geometries.push({
      id: 'courier',
      styleId: 'courier',
      position: courier,
    })
  }
  markerLayer.setGeometries(geometries)
}

function fitRoute() {
  if (!map || !routePath.length) return
  try {
    const lngs = routePath.map(lngOf)
    const lats = routePath.map(latOf)
    const sw = new tmap.LatLng(Math.min(...lats), Math.min(...lngs))
    const ne = new tmap.LatLng(Math.max(...lats), Math.max(...lngs))
    const bounds = new tmap.LatLngBounds(sw, ne)
    map.fitBounds(bounds, { padding: 60 })
  } catch {
    const mid = routePath[Math.floor(routePath.length / 2)]
    map.setCenter(mid)
    map.setZoom(9)
  }
}

function drawRoute(from: any, to: any) {
  originGeom = { id: 'origin', styleId: 'origin', position: from }
  destGeom = { id: 'dest', styleId: 'dest', position: to }
  routePath = curvePoints(from, to)
  lineLayer.setGeometries([{ id: 'route', styleId: 'route', paths: routePath }])
  updateMarkers()
  fitRoute()
}

function cityFallback(): any | null {
  const address = props.logistics.destAddress || ''
  for (const [name, lng, lat] of CITY_CENTERS) {
    if (address.includes(name)) {
      return new tmap.LatLng(lat, lng)
    }
  }
  return null
}

function resolveDestination(): Promise<any> {
  return new Promise((resolve, reject) => {
    try {
      if (!tmap.service?.Geocoder) {
        reject(new Error('腾讯地图 Geocoder 不可用'))
        return
      }
      const geocoder = new tmap.service.Geocoder()
      geocoder.getLocation({ address: props.logistics.destAddress })
        .then((result: any) => {
          const location = result?.result?.location || result?.location
          if (location && location.lng != null && location.lat != null) {
            resolve(new tmap.LatLng(Number(location.lat), Number(location.lng)))
          } else {
            reject(new Error('地址解析为空'))
          }
        })
        .catch(reject)
    } catch (e) {
      reject(e)
    }
  })
}

async function init() {
  try {
    mapError.value = ''
    tmap = await loadTMap()
    if (!mapEl.value) return
    map = new tmap.Map(mapEl.value, {
      center: new tmap.LatLng(
        Number(props.logistics.originLat) || 30.2741,
        Number(props.logistics.originLng) || 120.1551,
      ),
      zoom: 10,
      viewMode: '2D',
    })
    markerLayer = new tmap.MultiMarker({
      map,
      styles: {
        origin: new tmap.MarkerStyle({
          width: 24,
          height: 24,
          anchor: { x: 12, y: 12 },
          src: dotSvg('#2f80ed'),
        }),
        dest: new tmap.MarkerStyle({
          width: 28,
          height: 28,
          anchor: { x: 14, y: 14 },
          src: dotSvg('#d6336c', 28),
        }),
        courier: new tmap.MarkerStyle({
          width: 34,
          height: 34,
          anchor: { x: 17, y: 17 },
          src: courierSvg(),
        }),
      },
      geometries: [],
    })
    lineLayer = new tmap.MultiPolyline({
      map,
      styles: {
        route: new tmap.PolylineStyle({
          color: '#d6336c',
          width: 5,
          lineCap: 'round',
        }),
      },
      geometries: [],
    })

    const origin = new tmap.LatLng(
      Number(props.logistics.originLat) || 30.2741,
      Number(props.logistics.originLng) || 120.1551,
    )
    const dest = await resolveDestination().catch(() => cityFallback())
    if (!dest) {
      mapError.value = '收货地址解析失败，暂时无法显示地图'
      return
    }
    drawRoute(origin, dest)
    loaded = true
  } catch (e) {
    mapError.value = e instanceof Error ? e.message : '地图加载失败'
  }
}

watch(() => props.logistics.progress, () => {
  if (loaded) updateMarkers()
})

onMounted(init)

onBeforeUnmount(() => {
  markerLayer = null
  lineLayer = null
  routePath = []
  map?.destroy()
  map = null
})
</script>

<template>
  <div class="map-wrap">
    <div v-if="mapError" class="map-fallback">
      <p>{{ mapError }}</p>
      <p class="map-hint">
        在 <code>frontend/.env.local</code> 配置 <code>VITE_TMAP_KEY</code> 后显示腾讯地图
      </p>
    </div>
    <div v-show="!mapError" ref="mapEl" class="map-canvas"></div>
    <div v-if="!mapError" class="map-legend">
      <span class="legend-dot blue" />发货仓
      <span class="legend-arrow">→</span>
      <span class="legend-dot pink" />收货地址
      <span class="legend-progress">运输进度 {{ logistics.progress }}%</span>
    </div>
  </div>
</template>

<style scoped>
.map-wrap {
  position: relative;
  margin-top: 14px;
  overflow: hidden;
  background: #f8f9fb;
  border: 1px solid var(--c-border);
  border-radius: 10px;
}

.map-canvas {
  width: 100%;
  height: 320px;
}

.map-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 220px;
  padding: 22px;
  font-size: 13px;
  color: var(--c-text-2);
  text-align: center;
}

.map-fallback p {
  margin: 0;
}

.map-hint {
  font-size: 11px;
  color: var(--c-text-3);
}

.map-hint code {
  padding: 1px 4px;
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 11px;
  color: var(--c-pink);
  background: var(--c-pink-tint);
  border-radius: 4px;
}

.map-legend {
  position: absolute;
  left: 10px;
  bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 9px;
  font-size: 11px;
  color: var(--c-text-2);
  background: rgba(255, 255, 255, 0.92);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.legend-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-dot.blue {
  background: #2f80ed;
}

.legend-dot.pink {
  background: var(--c-pink);
}

.legend-arrow {
  color: var(--c-text-3);
}

.legend-progress {
  margin-left: 6px;
  color: var(--c-pink);
}
</style>
