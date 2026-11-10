<script setup lang="ts">
/**
 * 管理端移动 · 金额汇总
 * 只读视图：总览指标 + 按员工汇总 + 按类型汇总
 */
import { computed, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import MonthSwitcher from '@/components/MonthSwitcher.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppSheet from '@/components/AppSheet.vue'
import { useAuth, useTheme } from '@/stores/app'
import { useToast } from '@/stores/toast'
import type { Employee } from '@/core/types'
import type { MonthlyReport } from '@/api/report'
import {
  BizError,
  buildMonthlyReport,
  computeRecordSafe,
  getEmployeeName,
  getWorkTypeName,
  listEngineers,
} from '@/data/service'
import {
  currentMonthKey,
  formatDateLabel,
  formatHours,
  formatMoneyShort,
  formatMonthLabel,
  weekdayLabel,
} from '@/core/date'

const { dataVersion } = useAuth()
const { theme, toggle } = useTheme()
const toast = useToast()

const monthKey = ref(currentMonthKey())
const employeeFilter = ref('')

const engineers = ref<Employee[]>([])
const report = ref<MonthlyReport>({
  monthKey: '',
  summary: {
    recordCount: 0,
    countTotal: 0,
    normalHoursTotal: 0,
    overtimeHoursTotal: 0,
    byCountAmount: 0,
    byHourAmount: 0,
    totalAmount: 0,
  },
  byEmployee: [],
  byWorkType: [],
  approvedRecords: [],
  pendingCount: 0,
  rejectedCount: 0,
})
const loading = ref(false)

/** 加载汇总数据 */
async function loadData(): Promise<void> {
  loading.value = true
  try {
    const [emps, rep] = await Promise.all([
      listEngineers(),
      buildMonthlyReport({
        monthKey: monthKey.value,
        employeeId: employeeFilter.value || undefined,
      }),
    ])
    engineers.value = emps
    report.value = rep
  } catch (err) {
    toast.error(err instanceof BizError ? err.message : '加载失败')
  } finally {
    loading.value = false
  }
}

watch([monthKey, employeeFilter, dataVersion], () => {
  void loadData()
})

onMounted(() => {
  void loadData()
})

const drillEmployeeId = ref<string | null>(null)

const drillRecords = computed(() => {
  if (!drillEmployeeId.value) return []
  return report.value.approvedRecords.filter((r) => r.employeeId === drillEmployeeId.value)
})

const drillName = computed(() =>
  drillEmployeeId.value ? getEmployeeName(drillEmployeeId.value) : '',
)

const drillTotal = computed(() =>
  drillRecords.value.reduce((s, r) => s + computeRecordSafe(r).total, 0),
)
</script>

<template>
  <div class="m-summary">
    <header class="m-head">
      <div class="m-head__row">
        <div class="grow">
          <p class="t-eyebrow">Summary</p>
          <h1 class="m-head__title">金额汇总</h1>
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
      <!-- 主数字 -->
      <section class="hero">
        <span class="hero__label">提成总额</span>
        <p class="hero__value">
          <em>¥</em>{{ formatMoneyShort(report.summary.totalAmount) }}
        </p>
        <span class="hero__foot">
          {{ formatMonthLabel(monthKey) }} · {{ report.approvedRecords.length }} 条已确认
        </span>

        <div class="hero__strip">
          <div class="hero__cell">
            <span class="hero__cell-k">按次</span>
            <span class="hero__cell-v">{{ report.summary.countTotal }}<em>次</em></span>
          </div>
          <div class="hero__cell">
            <span class="hero__cell-k">正常</span>
            <span class="hero__cell-v">{{ formatHours(report.summary.normalHoursTotal) }}</span>
          </div>
          <div class="hero__cell">
            <span class="hero__cell-k">加班</span>
            <span class="hero__cell-v hero__cell-v--warn">
              {{ formatHours(report.summary.overtimeHoursTotal) }}
            </span>
          </div>
        </div>
      </section>

      <!-- 未计入提示 -->
      <div v-if="report.pendingCount > 0 || report.rejectedCount > 0" class="alert alert--warning">
        <AppIcon name="warning" :size="15" />
        <span>
          另有
          <strong v-if="report.pendingCount > 0">{{ report.pendingCount }} 条待确认</strong>
          <template v-if="report.pendingCount > 0 && report.rejectedCount > 0">、</template>
          <strong v-if="report.rejectedCount > 0">{{ report.rejectedCount }} 条已驳回</strong>
          未计入核算
        </span>
      </div>

      <!-- 员工筛选 -->
      <div class="field">
        <label class="field__label" for="m-emp-filter">按员工筛选</label>
        <select id="m-emp-filter" v-model="employeeFilter" class="select">
          <option value="">全部员工</option>
          <option v-for="emp in engineers" :key="emp.id" :value="emp.id">{{ emp.name }}</option>
        </select>
      </div>

      <!-- 按员工汇总 -->
      <section class="block">
        <div class="sec-bar">
          <span class="sec-bar__title">按员工汇总</span>
          <span class="block__hint">按金额排序</span>
        </div>

        <div v-if="report.byEmployee.length === 0" class="block__empty">
          <EmptyState icon="chart" title="本月暂无已确认记录" desc="确认记录后显示汇总" />
        </div>

        <div v-else class="emp-list">
          <button
            v-for="item in report.byEmployee"
            :key="item.employeeId"
            type="button"
            class="emp-row"
            @click="drillEmployeeId = item.employeeId"
          >
            <span class="emp-row__avatar">{{ item.employeeName.slice(0, 1) }}</span>
            <span class="emp-row__body">
              <span class="emp-row__top">
                <span class="emp-row__name">{{ item.employeeName }}</span>
                <span class="emp-row__amount tabular">
                  ¥{{ formatMoneyShort(item.summary.totalAmount) }}
                </span>
              </span>
              <span class="emp-row__meta tabular">
                {{ item.summary.countTotal }} 次
                <span class="dot-sep">·</span>
                正常 {{ formatHours(item.summary.normalHoursTotal) }}
                <span class="dot-sep">·</span>
                加班 {{ formatHours(item.summary.overtimeHoursTotal) }}
              </span>
            </span>
            <AppIcon name="chevron-right" :size="15" class="emp-row__arrow" />
          </button>

          <div class="emp-row emp-row--total">
            <span class="emp-row__body">
              <span class="emp-row__top">
                <span class="emp-row__name">合计</span>
                <span class="emp-row__amount tabular">
                  ¥{{ formatMoneyShort(report.summary.totalAmount) }}
                </span>
              </span>
            </span>
          </div>
        </div>
      </section>

      <!-- 按类型汇总 -->
      <section v-if="report.byWorkType.length > 0" class="block">
        <div class="sec-bar">
          <span class="sec-bar__title">按类型汇总</span>
        </div>
        <div class="type-list">
          <div v-for="item in report.byWorkType" :key="item.workTypeId" class="type-row">
            <div class="type-row__top">
              <span class="type-row__name">{{ item.workTypeName }}</span>
              <span class="tag" :class="item.billingMode === 'BY_HOUR' ? 'tag--hour' : 'tag--count'">
                {{ item.billingMode === 'BY_HOUR' ? '按小时' : '按次' }}
              </span>
              <span class="type-row__amount tabular">
                ¥{{ formatMoneyShort(item.summary.totalAmount) }}
              </span>
            </div>
            <div class="type-row__bar">
              <div
                class="type-row__fill"
                :style="{
                  width: `${
                    report.summary.totalAmount > 0
                      ? Math.max(3, (item.summary.totalAmount / report.summary.totalAmount) * 100)
                      : 0
                  }%`,
                }"
              />
            </div>
            <div class="type-row__meta tabular">
              {{ item.summary.recordCount }} 条
              <span class="dot-sep">/</span>
              正常 {{ formatHours(item.summary.normalHoursTotal) }}
              <span class="dot-sep">/</span>
              加班 {{ formatHours(item.summary.overtimeHoursTotal) }}
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 下钻明细 -->
    <AppSheet
      :open="!!drillEmployeeId"
      :title="`${drillName} · 核算明细`"
      @close="drillEmployeeId = null"
    >
      <div class="drill-list">
        <div class="drill-total">
          <span>合计</span>
          <span class="tabular">¥{{ formatMoneyShort(drillTotal) }}</span>
        </div>
        <div v-for="record in drillRecords" :key="record.id" class="drill-item">
          <div class="row row--between">
            <span class="fw-600 text-sm">{{ getWorkTypeName(record.workTypeId) }}</span>
            <span class="fw-700 tabular">¥{{ formatMoneyShort(computeRecordSafe(record).total) }}</span>
          </div>
          <p class="text-xs text-tertiary">
            {{ formatDateLabel(record.serviceDate) }} {{ weekdayLabel(record.serviceDate) }} ·
            {{ record.startTime }}<template v-if="record.endTime">~{{ record.endTime }}</template>
          </p>
          <p class="drill-item__explain">{{ computeRecordSafe(record).explanation }}</p>
        </div>
      </div>
      <template #footer>
        <button type="button" class="btn btn--secondary btn--block" @click="drillEmployeeId = null">
          关闭
        </button>
      </template>
    </AppSheet>
  </div>
</template>

<style scoped>
.m-summary {
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
  gap: var(--space-md);
  padding: var(--space-sm) var(--space-md) var(--space-lg);
}

/* 主数字 */
.hero {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-lg) var(--space-md) var(--space-md);
  border-radius: var(--radius-xl);
  background: linear-gradient(150deg, #0037a8 0%, #0052ff 52%, #4d7cff 100%);
  color: #fff;
  box-shadow: 0 6px 22px rgba(0, 82, 255, 0.28);
}

.hero__label {
  font-size: var(--text-2xs);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wider);
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.72);
}

.hero__value {
  font-size: var(--text-3xl);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-tight);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  margin-top: 2px;
}

.hero__value em {
  font-style: normal;
  font-size: var(--text-md);
  font-weight: var(--weight-medium);
  opacity: 0.7;
  margin-right: 2px;
}

.hero__foot {
  font-size: var(--text-2xs);
  color: rgba(255, 255, 255, 0.66);
}

.hero__strip {
  display: flex;
  align-items: stretch;
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}

.hero__cell {
  display: flex;
  flex-direction: column;
  gap: 1px;
  flex: 1 1 0;
  padding-left: var(--space-sm);
  border-left: 1px solid rgba(255, 255, 255, 0.18);
}

.hero__cell:first-child {
  padding-left: 0;
  border-left: 0;
}

.hero__cell-k {
  font-size: var(--text-2xs);
  color: rgba(255, 255, 255, 0.66);
}

.hero__cell-v {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  font-variant-numeric: tabular-nums;
}

.hero__cell-v em {
  font-style: normal;
  font-size: var(--text-2xs);
  opacity: 0.7;
  margin-left: 1px;
}

.hero__cell-v--warn {
  color: #fde047;
}

/* 区块 */
.block {
  display: flex;
  flex-direction: column;
}

.block__hint {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
}

.block__empty {
  background: var(--color-surface);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
}

/* 员工列表 */
.emp-list {
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.emp-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  padding: 11px var(--space-md);
  border: 0;
  border-bottom: 1px solid var(--color-divider);
  background: transparent;
  text-align: left;
  color: inherit;
  transition: background-color var(--duration-fast) var(--ease-in-out);
}

.emp-row:last-child {
  border-bottom: 0;
}

.emp-row:hover {
  background: var(--color-surface-muted);
}

.emp-row--total {
  background: var(--color-surface-muted);
  cursor: default;
}

.emp-row__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 50%;
  background: var(--color-accent-soft);
  color: var(--color-accent-soft-fg);
  font-size: var(--text-2xs);
  font-weight: var(--weight-bold);
}

.emp-row__body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  flex: 1 1 auto;
  min-width: 0;
}

.emp-row__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
}

.emp-row__name {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
}

.emp-row__amount {
  font-size: var(--text-sm);
  font-weight: var(--weight-bold);
}

.emp-row__meta {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
}

.emp-row__arrow {
  color: var(--color-fg-tertiary);
  flex: 0 0 auto;
}

/* 类型列表 */
.type-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.type-row__top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.type-row__name {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
}

.type-row__amount {
  margin-left: auto;
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
}

.type-row__bar {
  height: 4px;
  border-radius: var(--radius-full);
  background: var(--color-surface-muted);
  overflow: hidden;
}

.type-row__fill {
  height: 100%;
  border-radius: var(--radius-full);
  background: var(--grad-accent);
}

.type-row__meta {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  margin-top: 4px;
}

/* 下钻 */
.drill-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.drill-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px var(--space-md);
  border-radius: var(--radius-md);
  background: var(--color-accent-soft);
  color: var(--color-accent-soft-fg);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
}

.drill-item {
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.drill-item__explain {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  line-height: var(--leading-relaxed);
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px dashed var(--color-divider);
}
</style>
