<script setup lang="ts">
/**
 * 地图定位展示
 *
 * 设计要点：
 * - 使用 Leaflet + CartoDB 浅色瓦片（无需 API Key，国内可访问）
 * - 单点模式：显示一个定位标记 + 精度圈
 * - 多点模式：显示多条记录的位置，自动适配视野
 * - 无定位时显示占位提示，不阻断页面
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import AppIcon from './AppIcon.vue'
import { wgs84ToGcj02 } from '@/core/coord'

export interface MapPoint {
  id: string
  lat: number
  lng: number
  label: string
  sublabel?: string
}

const props = withDefaults(
  defineProps<{
    points: MapPoint[]
    /** 高度 */
    height?: number
    /** 是否显示缩放控件 */
    zoomControl?: boolean
    /** 单点时的缩放级别 */
    zoom?: number
    /** 是否可交互（详情页内嵌时通常关闭拖拽） */
    interactive?: boolean
  }>(),
  { height: 200, zoomControl: false, zoom: 16, interactive: true },
)

const mapEl = ref<HTMLDivElement | null>(null)
const loadError = ref(false)
let map: L.Map | null = null
let layerGroup: L.LayerGroup | null = null

const validPoints = computed(() =>
  props.points.filter(
    (p) => Number.isFinite(p.lat) && Number.isFinite(p.lng) && p.lat !== 0 && p.lng !== 0,
  ),
)

const hasPoints = computed(() => validPoints.value.length > 0)

/** 自定义标记图标（避免 Leaflet 默认图标的路径问题） */
function makeIcon(index: number, isPrimary: boolean): L.DivIcon {
  const size = isPrimary ? 30 : 24
  return L.divIcon({
    className: 'map-pin-marker',
    html: `<span class="map-pin-marker__dot${isPrimary ? ' map-pin-marker__dot--primary' : ''}">${
      isPrimary ? '' : index + 1
    }</span>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  })
}

function render(): void {
  if (!map || !layerGroup) return
  layerGroup.clearLayers()

  const pts = validPoints.value
  if (pts.length === 0) return

  pts.forEach((p, i) => {
    const isPrimary = pts.length === 1 || i === 0
    // GPS 原始坐标为 WGS-84，高德瓦片为 GCJ-02，需转换后再绘制
    const [gLat, gLng] = wgs84ToGcj02(p.lat, p.lng)
    const marker = L.marker([gLat, gLng], {
      icon: makeIcon(i, isPrimary),
      title: p.label,
    })

    const sub = p.sublabel ? `<br/><span class="map-popup__sub">${p.sublabel}</span>` : ''
    marker.bindPopup(
      `<div class="map-popup"><strong>${p.label}</strong>${sub}</div>`,
      { closeButton: false, offset: [0, -6] },
    )

    marker.addTo(layerGroup!)

    // 单点时显示精度圈
    if (pts.length === 1) {
      L.circle([gLat, gLng], {
        radius: 60,
        color: '#0052ff',
        weight: 1,
        opacity: 0.35,
        fillColor: '#0052ff',
        fillOpacity: 0.08,
      }).addTo(layerGroup!)
    }
  })

  if (pts.length === 1) {
    const [gLat, gLng] = wgs84ToGcj02(pts[0].lat, pts[0].lng)
    map.setView([gLat, gLng], props.zoom)
  } else {
    const bounds = L.latLngBounds(
      pts.map((p) => {
        const [gLat, gLng] = wgs84ToGcj02(p.lat, p.lng)
        return [gLat, gLng] as [number, number]
      }),
    )
    map.fitBounds(bounds, { padding: [28, 28], maxZoom: 16 })
  }
}

function init(): void {
  if (!mapEl.value || map) return
  try {
    map = L.map(mapEl.value, {
      zoomControl: props.zoomControl,
      attributionControl: false,
      dragging: props.interactive,
      scrollWheelZoom: false,
      doubleClickZoom: props.interactive,
      touchZoom: props.interactive,
      keyboard: false,
    })

    // 高德瓦片（GCJ-02 坐标系，国内访问稳定、无需 Key）
    L.tileLayer(
      'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
      {
        maxZoom: 18,
        subdomains: ['1', '2', '3', '4'],
      },
    ).addTo(map)

    layerGroup = L.layerGroup().addTo(map)
    render()
  } catch (err) {
    console.error('[map] 初始化失败', err)
    loadError.value = true
  }
}

onMounted(() => {
  // 等容器完成布局再初始化，避免尺寸为 0
  requestAnimationFrame(() => init())
})

watch(
  () => props.points,
  () => render(),
  { deep: true },
)

onBeforeUnmount(() => {
  map?.remove()
  map = null
  layerGroup = null
})
</script>

<template>
  <div class="map-wrap" :style="{ height: `${height}px` }">
    <div v-show="hasPoints && !loadError" ref="mapEl" class="map-canvas" />

    <!-- 无定位占位 -->
    <div v-if="!hasPoints" class="map-empty">
      <AppIcon name="map-pin" :size="22" :stroke-width="1.6" />
      <span>该记录未获取定位</span>
      <span class="map-empty__hint">定位失败不影响记录有效性</span>
    </div>

    <!-- 加载失败占位 -->
    <div v-else-if="loadError" class="map-empty">
      <AppIcon name="warning" :size="22" :stroke-width="1.6" />
      <span>地图加载失败</span>
      <span class="map-empty__hint">请检查网络后重试</span>
    </div>

    <!-- 坐标角标 -->
    <div v-if="hasPoints && !loadError" class="map-coords mono">
      {{ validPoints[0].lat.toFixed(5) }}, {{ validPoints[0].lng.toFixed(5) }}
    </div>
  </div>
</template>

<style scoped>
.map-wrap {
  position: relative;
  width: 100%;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
}

.map-canvas {
  width: 100%;
  height: 100%;
  z-index: 0;
}

.map-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  color: var(--color-fg-tertiary);
  font-size: var(--text-xs);
  background:
    repeating-linear-gradient(
      45deg,
      transparent,
      transparent 10px,
      color-mix(in srgb, var(--color-border) 40%, transparent) 10px,
      color-mix(in srgb, var(--color-border) 40%, transparent) 11px
    ),
    var(--color-surface-muted);
}

.map-empty__hint {
  font-size: var(--text-2xs);
  opacity: 0.75;
}

.map-coords {
  position: absolute;
  left: 8px;
  bottom: 8px;
  z-index: 400;
  padding: 2px 7px;
  border-radius: var(--radius-xs);
  background: color-mix(in srgb, var(--color-surface) 88%, transparent);
  backdrop-filter: blur(6px);
  font-size: var(--text-2xs);
  color: var(--color-fg-secondary);
  border: 1px solid var(--color-border);
}
</style>

<style>
/* 标记与弹窗样式（非 scoped，因为 Leaflet 动态插入 DOM） */
.map-pin-marker {
  background: transparent;
  border: 0;
}

.map-pin-marker__dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #0052ff;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  font-family: var(--font-mono);
  border: 2px solid #fff;
  box-shadow: 0 2px 8px rgba(0, 82, 255, 0.45);
}

.map-pin-marker__dot--primary {
  background: #0052ff;
  box-shadow:
    0 0 0 4px rgba(0, 82, 255, 0.18),
    0 2px 10px rgba(0, 82, 255, 0.5);
}

/* 主标记呼吸动画 */
.map-pin-marker__dot--primary::after {
  content: '';
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  border: 2px solid rgba(0, 82, 255, 0.4);
  animation: map-pulse 2s ease-out infinite;
}

@keyframes map-pulse {
  0% {
    transform: scale(0.7);
    opacity: 0.9;
  }
  100% {
    transform: scale(1.6);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .map-pin-marker__dot--primary::after {
    animation: none;
  }
}

.leaflet-container {
  font-family: var(--font-sans);
  background: var(--color-surface-muted);
}

.map-popup {
  font-size: 12px;
  line-height: 1.45;
  color: #0f172a;
}

.map-popup strong {
  font-weight: 600;
}

.map-popup__sub {
  color: #64748b;
  font-size: 11px;
}

.leaflet-popup-content-wrapper {
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.16);
}

.leaflet-popup-content {
  margin: 8px 10px;
}

/* 深色主题下瓦片降亮，避免刺眼 */
:root[data-theme='dark'] .leaflet-tile-pane {
  filter: invert(1) hue-rotate(180deg) brightness(0.92) contrast(0.92);
}

:root[data-theme='dark'] .leaflet-popup-content-wrapper,
:root[data-theme='dark'] .leaflet-popup-tip {
  background: #0f1729;
  color: #f1f5f9;
}

:root[data-theme='dark'] .map-popup {
  color: #f1f5f9;
}

:root[data-theme='dark'] .map-popup__sub {
  color: #8b9cb3;
}
</style>
