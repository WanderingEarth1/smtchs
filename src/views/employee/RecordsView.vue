<script setup lang="ts">
/**
 * 我的记录列表（REC-04）
 *
 * 排版策略：
 * - 顶部汇总用「主数字 + 内联条」，不做三宫格
 * - 筛选用下划线式标签，不用分段控件
 * - 列表按日期分组，组头用日期 + 当日合计
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import MonthSwitcher from '@/components/MonthSwitcher.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuth } from '@/stores/app'
import {
  buildMyMonthlySummary,
  computeRecordSafe,
  getWorkTypeName,
  listMyRecords,
} from '@/data/service'
import type { RecordStatus, WorkRecord } from '@/core/types'
import {
  currentMonthKey,
  formatDateLabel,
  formatHours,
  formatMoneyShort,
  weekdayLabel,
} from '@/core/date'

const route = useRoute()
const router = useRouter()
const { currentUser, dataVersion } = useAuth()

const monthKey = ref(currentMonthKey())
const statusFilter = ref<RecordStatus | 'ALL'>('ALL')

watch(
  () => route.query.status,
  (v) => {
    if (typeof v === 'string' && ['PENDING', 'APPROVED', 'REJECTED'].includes(v)) {
      statusFilter.value = v as RecordStatus
    }
  },
  { immediate: true },
)

const records = computed(() => {
  void dataVersion.value
  const user = currentUser.value
  if (!user) return []
  return listMyRecords(user.id, monthKey.value, statusFilter.value)
})

/** 按日期分组 */
const grouped = computed(() => {
  const map = new Map<string, WorkRecord[]>()
  for (const r of records.value) {
    if (!map.has(r.serviceDate)) map.set(r.serviceDate, [])
    map.get(r.serviceDate)!.push(r)
  }
  return [...map.entries()].map(([date, items]) => ({
    date,
    items,
    amount: items
      .filter((r) => r.status === 'APPROVED')
      .reduce((s, r) => s + computeRecordSafe(r).total, 0),
  }))
})

const counts = computed(() => {
  void dataVersion.value
  const user = currentUser.value
  if (!user) return { ALL: 0, PENDING: 0, APPROVED: 0, REJECTED: 0 }
  const all = listMyRecords(user.id, monthKey.value)
  return {
    ALL: all.length,
    PENDING: all.filter((r) => r.status === 'PENDING').length,
    APPROVED: all.filter((r) => r.status === 'APPROVED').length,
    REJECTED: all.filter((r) => r.status === 'REJECTED').length,
  }
})

const monthSummary = computed(() => {
  void dataVersion.value
  const user = currentUser.value
  if (!user) return null
  return buildMyMonthlySummary(user.id, monthKey.value)
})

const filters: Array<{ key: RecordStatus | 'ALL'; label: string }> = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING', label: '待确认' },
  { key: 'APPROVED', label: '已确认' },
  { key: 'REJECTED', label: '已驳回' },
]

function goDetail(id: string): void {
  void router.push(`/e/records/${id}`)
}

function goNew(): void {
  void router.push('/e/records/new')
}
</script>

<template>
  <div class="records">
    <header class="records__head">
      <div class="records__head-row">
        <h1 class="records__title">我的记录</h1>
        <button type="button" class="records__add" @click="goNew">
          <AppIcon name="plus" :size="15" :stroke-width="2.6" />
          新增
        </button>
      </div>
      <MonthSwitcher v-model="monthKey" />
    </header>

    <!-- 汇总：主数字 + 内联条 -->
    <section v-if="monthSummary" class="summary">
      <div class="summary__main">
        <span class="summary__label">已确认提成</span>
        <span class="summary__amount">
          <em>¥</em>{{ formatMoneyShort(monthSummary.summary.totalAmount) }}
        </span>
      </div>
      <div class="summary__strip">
        <div class="summary__cell">
          <span class="summary__cell-k">按次</span>
          <span class="summary__cell-v">{{ monthSummary.summary.countTotal }}</span>
        </div>
        <div class="summary__cell">
          <span class="summary__cell-k">正常</span>
          <span class="summary__cell-v">{{ formatHours(monthSummary.summary.normalHoursTotal) }}</span>
        </div>
        <div class="summary__cell">
          <span class="summary__cell-k">加班</span>
          <span class="summary__cell-v summary__cell-v--warn">
            {{ formatHours(monthSummary.summary.overtimeHoursTotal) }}
          </span>
        </div>
      </div>
    </section>

    <!-- 筛选：下划线式 -->
    <nav class="tabs" aria-label="按状态筛选">
      <button
        v-for="f in filters"
        :key="f.key"
        type="button"
        class="tabs__item"
        :class="{ 'tabs__item--active': statusFilter === f.key }"
        :aria-current="statusFilter === f.key ? 'true' : undefined"
        @click="statusFilter = f.key"
      >
        {{ f.label }}
        <span class="tabs__count">{{ counts[f.key] }}</span>
      </button>
    </nav>

    <!-- 空状态 -->
    <div v-if="records.length === 0" class="records__empty">
      <EmptyState
        icon="inbox"
        :title="statusFilter === 'ALL' ? '本月还没有记录' : '没有符合条件的记录'"
        :desc="
          statusFilter === 'ALL'
            ? '点击右上角「新增」记录你的第一次上门服务'
            : '试试切换其他状态筛选'
        "
      >
        <button
          v-if="statusFilter === 'ALL'"
          type="button"
          class="btn btn--primary btn--sm mt-sm"
          @click="goNew"
        >
          <AppIcon name="plus" :size="14" :stroke-width="2.6" />
          新增记录
        </button>
      </EmptyState>
    </div>

    <!-- 按日期分组 -->
    <div v-else class="groups">
      <section v-for="group in grouped" :key="group.date" class="group">
        <div class="group__head">
          <span class="group__date">
            {{ formatDateLabel(group.date) }}
            <em>{{ weekdayLabel(group.date) }}</em>
          </span>
          <span v-if="group.amount > 0" class="group__sum tabular">
            ¥{{ formatMoneyShort(group.amount) }}
          </span>
        </div>

        <div class="group__items">
          <button
            v-for="record in group.items"
            :key="record.id"
            type="button"
            class="row"
            :class="{ 'row--backfill': record.isBackfill }"
            @click="goDetail(record.id)"
          >
            <div class="row__main">
              <div class="row__top">
                <span class="row__type">{{ getWorkTypeName(record.workTypeId) }}</span>
                <StatusBadge :status="record.status" :is-backfill="record.isBackfill" />
              </div>

              <p class="row__content">{{ record.content }}</p>

              <div class="row__meta">
                <span class="row__meta-item">
                  <AppIcon name="clock" :size="11" />
                  {{ record.startTime }}<template v-if="record.endTime">–{{ record.endTime }}</template>
                </span>
                <span class="row__meta-item row__meta-item--place">
                  <AppIcon name="map-pin" :size="11" />
                  <span class="truncate">{{ record.location }}</span>
                </span>
                <span v-if="!record.geoCaptured" class="row__meta-item row__meta-item--warn">
                  未定位
                </span>
              </div>

              <p v-if="record.status === 'REJECTED' && record.rejectReason" class="row__reject">
                {{ record.rejectReason }}
              </p>
            </div>

            <div class="row__side">
              <span
                class="row__amount"
                :class="{ 'row__amount--muted': record.status !== 'APPROVED' }"
              >
                {{
                  record.status === 'APPROVED'
                    ? `¥${formatMoneyShort(computeRecordSafe(record).total)}`
                    : record.status === 'REJECTED'
                      ? '待修改'
                      : '待核算'
                }}
              </span>
              <span class="row__photos">{{ record.photos.length }} 张</span>
            </div>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.records {
  min-height: 100dvh;
  background: var(--color-bg);
  /* 预留底部标签栏高度 + 安全区，避免最后一条被遮挡 */
  padding-bottom: calc(var(--tabbar-height) + var(--space-lg) + var(--safe-bottom));
}

/* ---------- 头部 ---------- */
.records__head {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: calc(var(--safe-top) + var(--space-md)) var(--space-md) var(--space-sm);
  background: linear-gradient(180deg, var(--color-surface) 0%, var(--color-bg) 100%);
}

.records__head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.records__title {
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-snug);
}

.records__add {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  min-height: 32px;
  padding: 0 12px;
  border: 0;
  border-radius: var(--radius-full);
  background: var(--color-accent);
  color: var(--color-accent-fg);
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  box-shadow: 0 2px 8px rgba(3, 105, 161, 0.28);
  transition: transform var(--duration-fast) var(--ease-in-out);
}

.records__add:active {
  transform: scale(0.96);
}

/* ---------- 汇总 ---------- */
.summary {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-md);
  margin: 0 var(--space-md);
  padding: var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}

.summary__main {
  display: flex;
  flex-direction: column;
  gap: 1px;
  flex: 0 0 auto;
}

.summary__label {
  font-size: var(--text-2xs);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
  color: var(--color-fg-tertiary);
}

.summary__amount {
  font-size: var(--text-2xl);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-tight);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.summary__amount em {
  font-style: normal;
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--color-fg-tertiary);
  margin-right: 2px;
}

.summary__strip {
  display: flex;
  align-items: stretch;
  flex: 1 1 auto;
  justify-content: flex-end;
}

.summary__cell {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0;
  padding-left: var(--space-sm);
  border-left: 1px solid var(--color-divider);
  min-width: 0;
}

.summary__cell + .summary__cell {
  margin-left: var(--space-sm);
}

.summary__cell-k {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  white-space: nowrap;
}

.summary__cell-v {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.summary__cell-v--warn {
  color: var(--color-warning);
}

/* ---------- 筛选标签 ---------- */
.tabs {
  display: flex;
  gap: var(--space-md);
  margin: var(--space-md) var(--space-md) 0;
  padding-bottom: 0;
  border-bottom: 1px solid var(--color-border);
  overflow-x: auto;
  scrollbar-width: none;
}

.tabs::-webkit-scrollbar {
  display: none;
}

.tabs__item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 0 9px;
  border: 0;
  background: transparent;
  color: var(--color-fg-tertiary);
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  white-space: nowrap;
  transition: color var(--duration-fast) var(--ease-in-out);
}

.tabs__item:hover {
  color: var(--color-fg-secondary);
}

.tabs__item--active {
  color: var(--color-fg);
  font-weight: var(--weight-semibold);
}

.tabs__item--active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  border-radius: var(--radius-full);
  background: var(--color-accent);
}

.tabs__count {
  font-size: var(--text-2xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-fg-tertiary);
  padding: 0 4px;
  border-radius: var(--radius-xs);
  background: var(--color-surface-muted);
  min-width: 16px;
  text-align: center;
}

.tabs__item--active .tabs__count {
  background: var(--color-accent);
  color: var(--color-accent-fg);
}

/* ---------- 空状态 ---------- */
.records__empty {
  margin: var(--space-md);
  background: var(--color-surface);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-lg);
}

/* ---------- 分组 ---------- */
.groups {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
  padding: var(--space-md);
}

.group {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.group__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-left: 2px;
}

.group__date {
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--color-fg-secondary);
  letter-spacing: var(--tracking-wide);
}

.group__date em {
  font-style: normal;
  font-weight: var(--weight-normal);
  color: var(--color-fg-tertiary);
  margin-left: 4px;
}

.group__sum {
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--color-fg-secondary);
}

.group__items {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

/* ---------- 记录行 ---------- */
.row {
  display: flex;
  align-items: stretch;
  gap: var(--space-sm);
  width: 100%;
  padding: var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  text-align: left;
  color: inherit;
  transition:
    border-color var(--duration-fast) var(--ease-in-out),
    box-shadow var(--duration-fast) var(--ease-in-out);
}

.row:hover {
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-xs);
}

/* 补录：左侧竖条 */
.row--backfill {
  border-left: 3px solid var(--color-info);
}

.row__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1 1 auto;
  min-width: 0;
}

.row__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.row__type {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--color-fg);
}

.row__content {
  font-size: var(--text-xs);
  color: var(--color-fg-secondary);
  line-height: var(--leading-snug);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.row__meta {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  font-variant-numeric: tabular-nums;
}

.row__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

.row__meta-item--place {
  flex: 1 1 auto;
  overflow: hidden;
}

.row__meta-item--warn {
  color: var(--color-warning);
  flex: 0 0 auto;
}

.row__reject {
  font-size: var(--text-2xs);
  color: var(--color-danger);
  line-height: var(--leading-snug);
  padding-left: 6px;
  border-left: 2px solid var(--color-danger-border);
}

/* 右侧金额列 */
.row__side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-xs);
  flex: 0 0 auto;
  padding-left: var(--space-sm);
  border-left: 1px dashed var(--color-divider);
}

.row__amount {
  font-size: var(--text-sm);
  font-weight: var(--weight-bold);
  color: var(--color-fg);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.row__amount--muted {
  font-size: var(--text-2xs);
  font-weight: var(--weight-medium);
  color: var(--color-fg-tertiary);
}

.row__photos {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  white-space: nowrap;
}
</style>
