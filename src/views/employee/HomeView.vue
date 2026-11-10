<script setup lang="ts">
/**
 * 员工首页
 *
 * 交互要点：
 * - 新增记录：底部居中固定的大号圆形 FAB（渐变 + 光晕 + 呼吸感）
 * - 地图：展示今日/最近记录的位置分布
 * - 科技蓝主色 + 年轻化排版
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import MonthSwitcher from '@/components/MonthSwitcher.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import MapView, { type MapPoint } from '@/components/MapView.vue'
import { useAuth, useTheme } from '@/stores/app'
import {
  buildMyMonthlySummary,
  computeRecordSafe,
  getWorkTypeName,
  listMyRecords,
} from '@/data/service'
import {
  currentMonthKey,
  formatDateLabel,
  formatHours,
  formatMoneyShort,
  weekdayLabel,
} from '@/core/date'

const router = useRouter()
const { currentUser, dataVersion } = useAuth()
const { theme, toggle } = useTheme()

const monthKey = ref(currentMonthKey())

const summaryData = computed(() => {
  void dataVersion.value
  const user = currentUser.value
  if (!user) {
    return {
      summary: {
        recordCount: 0,
        countTotal: 0,
        normalHoursTotal: 0,
        overtimeHoursTotal: 0,
        byCountAmount: 0,
        byHourAmount: 0,
        totalAmount: 0,
      },
      pendingCount: 0,
      rejectedCount: 0,
      recordCount: 0,
    }
  }
  return buildMyMonthlySummary(user.id, monthKey.value)
})

const allRecords = computed(() => {
  void dataVersion.value
  const user = currentUser.value
  if (!user) return []
  return listMyRecords(user.id, monthKey.value)
})

const recentRecords = computed(() => allRecords.value.slice(0, 4))

/** 地图点位：本月有定位的记录 */
const mapPoints = computed<MapPoint[]>(() =>
  allRecords.value
    .filter((r) => r.geoCaptured && r.latitude !== null && r.longitude !== null)
    .slice(0, 12)
    .map((r) => ({
      id: r.id,
      lat: r.latitude!,
      lng: r.longitude!,
      label: getWorkTypeName(r.workTypeId),
      sublabel: `${formatDateLabel(r.serviceDate)} ${r.startTime} · ${r.location}`,
    })),
)

const geoCount = computed(() => mapPoints.value.length)

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayCount = computed(() => {
  const today = new Date()
  const key = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return allRecords.value.filter((r) => r.serviceDate === key).length
})

const todayLabel = computed(() => {
  const d = new Date().toISOString().slice(0, 10)
  return `${formatDateLabel(d)} ${weekdayLabel(d)}`
})

function goNew(): void {
  void router.push('/e/records/new')
}

function goRecords(status?: string): void {
  void router.push({ path: '/e/records', query: status ? { status } : {} })
}

function goDetail(id: string): void {
  void router.push(`/e/records/${id}`)
}
</script>

<template>
  <div class="home">
    <!-- 顶部 -->
    <header class="home-top">
      <div class="home-top__row">
        <div class="grow">
          <p class="home-top__date">{{ todayLabel }}</p>
          <h1 class="home-top__greet">{{ greeting }}，{{ currentUser?.name }}</h1>
        </div>
        <button
          type="button"
          class="home-top__theme"
          :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          @click="toggle"
        >
          <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" :size="17" />
        </button>
      </div>
    </header>

    <div class="home-body">
      <!-- 提成主卡 -->
      <section class="earn" aria-labelledby="earn-title">
        <div class="earn__glow" aria-hidden="true" />
        <div class="earn__top">
          <div>
            <p id="earn-title" class="earn__eyebrow">当月预估提成</p>
            <p class="earn__amount">
              <span class="earn__currency">¥</span>{{ formatMoneyShort(summaryData.summary.totalAmount) }}
            </p>
          </div>
          <MonthSwitcher v-model="monthKey" />
        </div>

        <div class="earn__strip">
          <div class="earn__cell">
            <span class="earn__cell-label">按次</span>
            <span class="earn__cell-value">{{ summaryData.summary.countTotal }}<em>次</em></span>
          </div>
          <div class="earn__cell">
            <span class="earn__cell-label">正常</span>
            <span class="earn__cell-value">{{ formatHours(summaryData.summary.normalHoursTotal) }}</span>
          </div>
          <div class="earn__cell">
            <span class="earn__cell-label">加班</span>
            <span class="earn__cell-value earn__cell-value--warn">
              {{ formatHours(summaryData.summary.overtimeHoursTotal) }}
            </span>
          </div>
          <div class="earn__cell">
            <span class="earn__cell-label">已确认</span>
            <span class="earn__cell-value">{{ summaryData.summary.recordCount }}<em>条</em></span>
          </div>
        </div>

        <p class="earn__foot">
          按次 ¥{{ formatMoneyShort(summaryData.summary.byCountAmount) }}
          <span class="dot-sep">/</span>
          按时 ¥{{ formatMoneyShort(summaryData.summary.byHourAmount) }}
        </p>
      </section>

      <!-- 待办 -->
      <div v-if="summaryData.pendingCount > 0 || summaryData.rejectedCount > 0" class="todo-list">
        <button
          v-if="summaryData.pendingCount > 0"
          type="button"
          class="todo todo--pending"
          @click="goRecords('PENDING')"
        >
          <span class="todo__mark" aria-hidden="true" />
          <span class="todo__body">
            <span class="todo__title">{{ summaryData.pendingCount }} 条待管理员确认</span>
            <span class="todo__desc">确认后计入当月提成</span>
          </span>
          <AppIcon name="chevron-right" :size="15" class="todo__arrow" />
        </button>

        <button
          v-if="summaryData.rejectedCount > 0"
          type="button"
          class="todo todo--rejected"
          @click="goRecords('REJECTED')"
        >
          <span class="todo__mark" aria-hidden="true" />
          <span class="todo__body">
            <span class="todo__title">{{ summaryData.rejectedCount }} 条被驳回</span>
            <span class="todo__desc">需修改后重新提交</span>
          </span>
          <AppIcon name="chevron-right" :size="15" class="todo__arrow" />
        </button>
      </div>

      <!-- 地图定位 -->
      <section class="map-block">
        <div class="sec-bar">
          <span class="sec-bar__title">服务地图</span>
          <span class="map-block__count">
            {{ geoCount > 0 ? `${geoCount} 个定位点` : '暂无定位' }}
          </span>
        </div>
        <MapView :points="mapPoints" :height="188" :zoom="13" />
      </section>

      <!-- 最近记录 -->
      <section class="recent">
        <div class="sec-bar">
          <span class="sec-bar__title">最近记录</span>
          <button type="button" class="recent__all" @click="goRecords()">
            全部
            <AppIcon name="chevron-right" :size="13" />
          </button>
        </div>

        <div v-if="recentRecords.length === 0" class="recent__empty">
          <EmptyState
            icon="inbox"
            title="本月还没有记录"
            desc="点击下方按钮记录你的第一次上门服务"
          />
        </div>

        <ol v-else class="timeline">
          <li v-for="record in recentRecords" :key="record.id" class="timeline__item">
            <div class="timeline__date">
              <span class="timeline__day">
                {{ formatDateLabel(record.serviceDate).replace('月', '/').replace('日', '') }}
              </span>
              <span class="timeline__week">{{ weekdayLabel(record.serviceDate) }}</span>
            </div>

            <button type="button" class="timeline__card" @click="goDetail(record.id)">
              <div class="timeline__head">
                <span class="timeline__type">{{ getWorkTypeName(record.workTypeId) }}</span>
                <StatusBadge :status="record.status" :is-backfill="record.isBackfill" />
              </div>

              <p class="timeline__content">{{ record.content }}</p>

              <div class="timeline__meta">
                <span class="timeline__meta-item">
                  <AppIcon name="clock" :size="11" />
                  {{ record.startTime }}<template v-if="record.endTime">–{{ record.endTime }}</template>
                </span>
                <span class="timeline__meta-item timeline__meta-item--place">
                  <AppIcon name="map-pin" :size="11" />
                  <span class="truncate">{{ record.location }}</span>
                </span>
                <span
                  class="timeline__amount"
                  :class="{ 'timeline__amount--muted': record.status !== 'APPROVED' }"
                >
                  {{
                    record.status === 'APPROVED'
                      ? `¥${formatMoneyShort(computeRecordSafe(record).total)}`
                      : record.status === 'REJECTED'
                        ? '待修改'
                        : '待核算'
                  }}
                </span>
              </div>
            </button>
          </li>
        </ol>
      </section>
    </div>

    <!-- 底部居中固定 FAB -->
    <div class="fab-dock">
      <button type="button" class="fab" aria-label="新增服务记录" @click="goNew">
        <span class="fab__ring" aria-hidden="true" />
        <AppIcon name="plus" :size="28" :stroke-width="2.6" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.home {
  min-height: 100dvh;
  background: var(--color-bg);
}

/* ---------- 顶部 ---------- */
.home-top {
  padding: calc(var(--safe-top) + var(--space-md)) var(--space-md) var(--space-sm);
  background: linear-gradient(180deg, var(--color-surface) 0%, var(--color-bg) 100%);
}

.home-top__row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
}

.home-top__date {
  font-size: var(--text-2xs);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wider);
  text-transform: uppercase;
  color: var(--color-fg-tertiary);
}

.home-top__greet {
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-snug);
  margin-top: 2px;
}

.home-top__theme {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: var(--color-surface);
  color: var(--color-fg-secondary);
  transition: all var(--duration-fast) var(--ease-in-out);
}

.home-top__theme:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.home-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
  /* 预留底部标签栏 + FAB 上浮部分 */
  padding: var(--space-sm) var(--space-md)
    calc(var(--tabbar-height) + 44px + var(--safe-bottom));
}

/* ---------- 提成主卡 ---------- */
.earn {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  padding: var(--space-lg) var(--space-md) var(--space-md);
  border-radius: var(--radius-2xl) var(--radius-2xl) var(--radius-lg) var(--radius-lg);
  background: linear-gradient(150deg, #0037a8 0%, #0052ff 52%, #4d7cff 100%);
  color: #fff;
  box-shadow: 0 8px 28px rgba(0, 82, 255, 0.32);
  overflow: hidden;
}

/* 光斑装饰 */
.earn__glow {
  position: absolute;
  right: -50px;
  top: -70px;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.28), transparent 68%);
  pointer-events: none;
}

.earn::after {
  content: '';
  position: absolute;
  right: -30px;
  bottom: -60px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.14);
  pointer-events: none;
}

.earn__top {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.earn__eyebrow {
  font-size: var(--text-2xs);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wider);
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.72);
}

.earn__amount {
  font-size: var(--text-4xl);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-tight);
  line-height: 1.05;
  margin-top: 4px;
  font-variant-numeric: tabular-nums;
}

.earn__currency {
  font-size: var(--text-lg);
  font-weight: var(--weight-medium);
  opacity: 0.7;
  margin-right: 3px;
  vertical-align: 6px;
}

.earn :deep(.month-switch) {
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.22);
}

.earn :deep(.month-switch__btn) {
  color: rgba(255, 255, 255, 0.9);
  width: 30px;
  height: 30px;
}

.earn :deep(.month-switch__btn:hover:not(:disabled)) {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.earn :deep(.month-switch__label) {
  color: #fff;
  font-size: var(--text-2xs);
  min-width: 74px;
  padding: 0 4px;
}

.earn__strip {
  position: relative;
  display: flex;
  align-items: stretch;
  padding-top: var(--space-md);
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}

.earn__cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1 1 0;
  min-width: 0;
  padding-left: var(--space-sm);
  border-left: 1px solid rgba(255, 255, 255, 0.18);
}

.earn__cell:first-child {
  padding-left: 0;
  border-left: 0;
}

.earn__cell-label {
  font-size: var(--text-2xs);
  color: rgba(255, 255, 255, 0.66);
  white-space: nowrap;
}

.earn__cell-value {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.earn__cell-value em {
  font-style: normal;
  font-size: var(--text-2xs);
  font-weight: var(--weight-normal);
  opacity: 0.7;
  margin-left: 1px;
}

.earn__cell-value--warn {
  color: #fde047;
}

.earn__foot {
  position: relative;
  font-size: var(--text-2xs);
  color: rgba(255, 255, 255, 0.66);
  font-variant-numeric: tabular-nums;
}

/* ---------- 待办 ---------- */
.todo-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.todo {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  padding: 10px var(--space-md) 10px calc(var(--space-md) + 6px);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  text-align: left;
  color: inherit;
  overflow: hidden;
  transition: border-color var(--duration-fast) var(--ease-in-out);
}

.todo:hover {
  border-color: var(--color-border-strong);
}

.todo__mark {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
}

.todo--pending .todo__mark {
  background: var(--color-warning);
}
.todo--rejected .todo__mark {
  background: var(--color-danger);
}

.todo__body {
  display: flex;
  flex-direction: column;
  gap: 0;
  flex: 1 1 auto;
  min-width: 0;
}

.todo__title {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--color-fg);
}

.todo__desc {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
}

.todo__arrow {
  color: var(--color-fg-tertiary);
  flex: 0 0 auto;
}

/* ---------- 地图 ---------- */
.map-block {
  display: flex;
  flex-direction: column;
}

.map-block__count {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  font-variant-numeric: tabular-nums;
}

/* ---------- 最近记录 ---------- */
.recent {
  display: flex;
  flex-direction: column;
}

.recent__all {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  border: 0;
  background: transparent;
  color: var(--color-accent);
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  padding: 2px 0;
}

.recent__empty {
  background: var(--color-surface);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-lg);
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.timeline__item {
  display: flex;
  align-items: stretch;
  gap: var(--space-sm);
}

.timeline__date {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  gap: 0;
  flex: 0 0 42px;
  padding-top: 10px;
}

.timeline__day {
  font-size: var(--text-sm);
  font-weight: var(--weight-bold);
  font-variant-numeric: tabular-nums;
  letter-spacing: var(--tracking-snug);
  color: var(--color-fg);
  line-height: 1.1;
}

.timeline__week {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  line-height: 1.4;
}

.timeline__card {
  display: flex;
  flex-direction: column;
  gap: 5px;
  flex: 1 1 auto;
  min-width: 0;
  padding: 10px var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  text-align: left;
  color: inherit;
  transition:
    border-color var(--duration-fast) var(--ease-in-out),
    box-shadow var(--duration-fast) var(--ease-in-out);
}

.timeline__card:hover {
  border-color: var(--color-accent);
  box-shadow: 0 2px 10px rgba(0, 82, 255, 0.1);
}

.timeline__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.timeline__type {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--color-fg);
}

.timeline__content {
  font-size: var(--text-xs);
  color: var(--color-fg-secondary);
  line-height: var(--leading-snug);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.timeline__meta {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  font-variant-numeric: tabular-nums;
}

.timeline__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

.timeline__meta-item--place {
  flex: 1 1 auto;
  overflow: hidden;
}

.timeline__amount {
  flex: 0 0 auto;
  font-size: var(--text-sm);
  font-weight: var(--weight-bold);
  color: var(--color-fg);
  font-variant-numeric: tabular-nums;
}

.timeline__amount--muted {
  font-size: var(--text-2xs);
  font-weight: var(--weight-medium);
  color: var(--color-fg-tertiary);
}

/* ---------- 底部居中 FAB ---------- */
.fab-dock {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  /* 骑跨在底部标签栏上沿 */
  bottom: calc(var(--tabbar-height) + var(--safe-bottom) - 26px);
  z-index: 45;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  pointer-events: none;
}

@media (min-width: 640px) {
  .fab-dock {
    /* 桌面端跟随居中容器 */
    left: 50%;
  }
}

.fab {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 62px;
  height: 62px;
  border: 0;
  border-radius: 50%;
  background: linear-gradient(140deg, #0052ff 0%, #4d7cff 100%);
  color: #fff;
  box-shadow:
    0 6px 20px rgba(0, 82, 255, 0.42),
    0 2px 6px rgba(0, 82, 255, 0.3);
  cursor: pointer;
  pointer-events: auto;
  transition:
    transform 180ms cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow 180ms var(--ease-out);
  /* 轻微呼吸浮动 */
  animation: fab-bob 3.2s ease-in-out infinite;
}

.fab:hover {
  box-shadow:
    0 10px 28px rgba(0, 82, 255, 0.5),
    0 3px 8px rgba(0, 82, 255, 0.34);
}

.fab:active {
  transform: scale(0.92);
  animation-play-state: paused;
}

/* 外圈光环 */
.fab__ring {
  position: absolute;
  inset: -7px;
  border-radius: 50%;
  border: 2px solid rgba(0, 82, 255, 0.22);
  pointer-events: none;
}

@keyframes fab-bob {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-4px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .fab {
    animation: none;
  }
}


</style>
