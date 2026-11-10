<script setup lang="ts">
/**
 * 管理端移动 · 详细记录数据（只读）
 * 支持按月 / 状态 / 员工 / 关键词筛选，点击查看详情
 */
import { computed, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import MonthSwitcher from '@/components/MonthSwitcher.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppSheet from '@/components/AppSheet.vue'
import MapView, { type MapPoint } from '@/components/MapView.vue'
import { useAuth, useTheme } from '@/stores/app'
import {
  computeRecordSafe,
  getEmployeeName,
  getWorkType,
  getWorkTypeName,
  listAllRecords,
  listEngineers,
} from '@/data/service'
import type { RecordStatus, WorkRecord } from '@/core/types'
import { ROUNDING_LABELS } from '@/core/billing'
import {
  currentMonthKey,
  formatDateLabel,
  formatFullDateTime,
  formatHours,
  formatMoney,
  formatMoneyShort,
  weekdayLabel,
} from '@/core/date'

const { dataVersion } = useAuth()
const { theme, toggle } = useTheme()

const monthKey = ref(currentMonthKey())
const statusFilter = ref<RecordStatus | 'ALL'>('ALL')
const employeeFilter = ref('')
const keyword = ref('')

const engineers = computed(() => {
  void dataVersion.value
  return listEngineers()
})

const records = computed(() => {
  void dataVersion.value
  return listAllRecords({
    monthKey: monthKey.value,
    status: statusFilter.value,
    employeeId: employeeFilter.value || undefined,
    keyword: keyword.value,
  })
})

const counts = computed(() => {
  void dataVersion.value
  const all = listAllRecords({
    monthKey: monthKey.value,
    employeeId: employeeFilter.value || undefined,
    keyword: keyword.value,
  })
  return {
    ALL: all.length,
    PENDING: all.filter((r) => r.status === 'PENDING').length,
    APPROVED: all.filter((r) => r.status === 'APPROVED').length,
    REJECTED: all.filter((r) => r.status === 'REJECTED').length,
  }
})

const filters: Array<{ key: RecordStatus | 'ALL'; label: string }> = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING', label: '待确认' },
  { key: 'APPROVED', label: '已确认' },
  { key: 'REJECTED', label: '已驳回' },
]

/* ---------------- 详情 ---------------- */
const detailId = ref<string | null>(null)

const detailRecord = computed<WorkRecord | null>(() => {
  void dataVersion.value
  if (!detailId.value) return null
  return records.value.find((r) => r.id === detailId.value) ?? null
})

const detailPoints = computed<MapPoint[]>(() => {
  const r = detailRecord.value
  if (!r || !r.geoCaptured || r.latitude === null || r.longitude === null) return []
  return [
    {
      id: r.id,
      lat: r.latitude,
      lng: r.longitude,
      label: getWorkTypeName(r.workTypeId),
      sublabel: r.location,
    },
  ]
})

const detailBilling = computed(() =>
  detailRecord.value ? computeRecordSafe(detailRecord.value) : null,
)

function resetFilters(): void {
  employeeFilter.value = ''
  keyword.value = ''
  statusFilter.value = 'ALL'
}

const hasFilter = computed(
  () => !!employeeFilter.value || !!keyword.value || statusFilter.value !== 'ALL',
)
</script>

<template>
  <div class="m-records">
    <header class="m-head">
      <div class="m-head__row">
        <div class="grow">
          <p class="t-eyebrow">Records</p>
          <h1 class="m-head__title">详细记录</h1>
        </div>
        <button
          type="button"
          class="m-head__theme"
          :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          @click="toggle"
        >
          <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" :size="17" />
        </button>
      </div>
      <MonthSwitcher v-model="monthKey" />
    </header>

    <div class="m-body">
      <!-- 搜索 -->
      <div class="search">
        <AppIcon name="search" :size="15" class="search__icon" />
        <input
          v-model="keyword"
          class="input search__input"
          type="search"
          placeholder="搜索员工 / 地点 / 内容"
          aria-label="搜索记录"
        />
      </div>

      <!-- 状态标签 -->
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

      <!-- 员工筛选 -->
      <div class="row gap-sm">
        <select v-model="employeeFilter" class="select grow" aria-label="按员工筛选">
          <option value="">全部员工</option>
          <option v-for="emp in engineers" :key="emp.id" :value="emp.id">{{ emp.name }}</option>
        </select>
        <button v-if="hasFilter" type="button" class="btn btn--ghost btn--sm" @click="resetFilters">
          <AppIcon name="x" :size="14" />
          清除
        </button>
      </div>

      <!-- 列表 -->
      <div v-if="records.length === 0" class="empty-box">
        <EmptyState icon="inbox" title="没有符合条件的记录" desc="试试调整筛选条件或切换月份" />
      </div>

      <div v-else class="rec-list">
        <button
          v-for="record in records"
          :key="record.id"
          type="button"
          class="rec-row"
          :class="{ 'rec-row--backfill': record.isBackfill }"
          @click="detailId = record.id"
        >
          <div class="rec-row__main">
            <div class="rec-row__top">
              <span class="rec-row__emp">{{ getEmployeeName(record.employeeId) }}</span>
              <span class="rec-row__type">{{ getWorkTypeName(record.workTypeId) }}</span>
              <StatusBadge :status="record.status" :is-backfill="record.isBackfill" />
            </div>
            <p class="rec-row__content">{{ record.content }}</p>
            <div class="rec-row__meta">
              <span>{{ formatDateLabel(record.serviceDate) }}</span>
              <span class="dot-sep">·</span>
              <span class="tabular">
                {{ record.startTime }}<template v-if="record.endTime">–{{ record.endTime }}</template>
              </span>
              <span class="dot-sep">·</span>
              <span class="truncate">{{ record.location }}</span>
            </div>
          </div>
          <div class="rec-row__side">
            <span class="rec-row__amount tabular">
              ¥{{ formatMoneyShort(computeRecordSafe(record).total) }}
            </span>
            <span v-if="!record.geoCaptured" class="rec-row__geo">未定位</span>
          </div>
        </button>
      </div>
    </div>

    <!-- 详情抽屉 -->
    <AppSheet
      :open="!!detailRecord"
      :title="detailRecord ? `${getEmployeeName(detailRecord.employeeId)} 的记录` : '记录详情'"
      @close="detailId = null"
    >
      <template v-if="detailRecord">
        <div class="sheet-detail">
          <div class="row row--between">
            <StatusBadge :status="detailRecord.status" :is-backfill="detailRecord.isBackfill" />
            <span class="mono text-xs text-tertiary">{{ detailRecord.serialNo }}</span>
          </div>

          <div class="kv-list">
            <div class="kv-row">
              <span class="kv-row__k">工作类型</span>
              <span class="kv-row__v">
                {{ getWorkTypeName(detailRecord.workTypeId) }}
                <span
                  class="tag"
                  :class="
                    getWorkType(detailRecord.workTypeId)?.billingMode === 'BY_HOUR'
                      ? 'tag--hour'
                      : 'tag--count'
                  "
                  style="margin-left: 6px"
                >
                  {{
                    getWorkType(detailRecord.workTypeId)?.billingMode === 'BY_HOUR'
                      ? '按小时'
                      : '按次'
                  }}
                </span>
              </span>
            </div>
            <div class="kv-row">
              <span class="kv-row__k">服务日期</span>
              <span class="kv-row__v">
                {{ formatDateLabel(detailRecord.serviceDate) }}
                {{ weekdayLabel(detailRecord.serviceDate) }}
              </span>
            </div>
            <div class="kv-row">
              <span class="kv-row__k">时间</span>
              <span class="kv-row__v tabular">
                {{ detailRecord.startTime
                }}<template v-if="detailRecord.endTime"> ~ {{ detailRecord.endTime }}</template>
              </span>
            </div>
            <div class="kv-row">
              <span class="kv-row__k">地点</span>
              <span class="kv-row__v">{{ detailRecord.location }}</span>
            </div>
            <div class="kv-row">
              <span class="kv-row__k">工作内容</span>
              <span class="kv-row__v">{{ detailRecord.content }}</span>
            </div>
            <div v-if="detailRecord.remark" class="kv-row">
              <span class="kv-row__k">备注</span>
              <span class="kv-row__v">{{ detailRecord.remark }}</span>
            </div>
            <div class="kv-row">
              <span class="kv-row__k">提交时间</span>
              <span class="kv-row__v">{{ formatFullDateTime(detailRecord.submittedAt) }}</span>
            </div>
          </div>

          <!-- 地图 -->
          <div class="sheet-section">
            <span class="sheet-section__title">定位</span>
            <MapView :points="detailPoints" :height="160" :zoom="16" :interactive="false" />
          </div>

          <!-- 凭证 -->
          <div class="sheet-section">
            <span class="sheet-section__title">现场凭证（{{ detailRecord.photos.length }}）</span>
            <div class="sheet-photos">
              <img
                v-for="(photo, i) in detailRecord.photos"
                :key="photo.id"
                :src="photo.dataUrl"
                :alt="`凭证 ${i + 1}`"
                class="sheet-photo"
              />
            </div>
          </div>

          <!-- 计费 -->
          <div v-if="detailBilling" class="billing-box">
            <p>{{ detailBilling.explanation }}</p>
            <div class="billing-box__total">
              <span>核算金额</span>
              <span class="billing-box__amount">¥{{ formatMoney(detailBilling.total) }}</span>
            </div>
            <p class="text-xs text-tertiary mt-sm">
              单价 ¥{{ formatMoney(detailRecord.billingSnapshot.unitPrice) }} · 加班起算
              {{ detailRecord.billingSnapshot.overtimeStart }} · ×{{
                detailRecord.billingSnapshot.overtimeMultiplier
              }}
              · {{ ROUNDING_LABELS[detailRecord.billingSnapshot.roundingRule] }}
            </p>
          </div>

          <div
            v-if="detailRecord.status === 'REJECTED' && detailRecord.rejectReason"
            class="alert alert--danger"
          >
            <AppIcon name="warning" :size="15" />
            <span>驳回原因：{{ detailRecord.rejectReason }}</span>
          </div>
        </div>
      </template>

      <template #footer>
        <button type="button" class="btn btn--secondary btn--block" @click="detailId = null">
          关闭
        </button>
      </template>
    </AppSheet>
  </div>
</template>

<style scoped>
.m-records {
  min-height: 100dvh;
  background: var(--color-bg);
}

.m-head {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: calc(var(--safe-top) + var(--space-md)) var(--space-md) var(--space-sm);
  background: linear-gradient(180deg, var(--color-surface) 0%, var(--color-bg) 100%);
}

.m-head__row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
}

.m-head__title {
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-snug);
  margin-top: 2px;
}

.m-head__theme {
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
}

.m-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md) var(--space-lg);
}

/* 搜索 */
.search {
  position: relative;
}

.search__icon {
  position: absolute;
  left: 11px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-fg-tertiary);
  pointer-events: none;
}

.search__input {
  padding-left: 34px;
}

/* 标签 */
.tabs {
  display: flex;
  gap: var(--space-md);
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

.empty-box {
  background: var(--color-surface);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
}

/* 记录列表 */
.rec-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.rec-row {
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

.rec-row:hover {
  border-color: var(--color-accent);
  box-shadow: 0 2px 10px rgba(0, 82, 255, 0.1);
}

.rec-row--backfill {
  border-left: 3px solid var(--color-info);
}

.rec-row__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1 1 auto;
  min-width: 0;
}

.rec-row__top {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.rec-row__emp {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
}

.rec-row__type {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  padding: 0 5px;
  border-radius: var(--radius-xs);
  background: var(--color-surface-muted);
}

.rec-row__content {
  font-size: var(--text-xs);
  color: var(--color-fg-secondary);
  line-height: var(--leading-snug);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.rec-row__meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  font-variant-numeric: tabular-nums;
  overflow: hidden;
}

.rec-row__side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 4px;
  flex: 0 0 auto;
  padding-left: var(--space-sm);
  border-left: 1px dashed var(--color-divider);
}

.rec-row__amount {
  font-size: var(--text-sm);
  font-weight: var(--weight-bold);
  white-space: nowrap;
}

.rec-row__geo {
  font-size: var(--text-2xs);
  color: var(--color-warning);
  white-space: nowrap;
}

/* 详情 */
.sheet-detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.sheet-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sheet-section__title {
  font-size: var(--text-2xs);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
  color: var(--color-fg-tertiary);
}

.sheet-photos {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.sheet-photo {
  width: 84px;
  height: 84px;
  object-fit: cover;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
}
</style>
