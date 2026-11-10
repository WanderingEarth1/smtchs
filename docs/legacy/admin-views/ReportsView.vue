<script setup lang="ts">
/**
 * 统计核算与导出（STA-01 ~ STA-04）
 * - 月度报表，支持按员工 / 工作类型筛选
 * - 汇总视图 + 按员工汇总（金额排序）+ 下钻明细
 * - 仅已确认记录计入核算，未计入条数单独提示
 * - 一键导出 Excel（双 Sheet）
 */
import { computed, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import MonthSwitcher from '@/components/MonthSwitcher.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppSheet from '@/components/AppSheet.vue'
import { useAuth } from '@/stores/app'
import { useToast } from '@/stores/toast'
import {
  buildMonthlyReport,
  computeRecordSafe,
  getEmployeeName,
  getWorkType,
  getWorkTypeName,
  listEngineers,
  listWorkTypes,
} from '@/data/service'
import { exportMonthlyReport } from '@/data/export'
import type { WorkRecord, WorkType } from '@/core/types'
import { ROUNDING_LABELS } from '@/core/billing'
import {
  currentMonthKey,
  formatDateLabel,
  formatHours,
  formatMoney,
  formatMoneyShort,
  formatMonthLabel,
  weekdayLabel,
} from '@/core/date'

const { dataVersion } = useAuth()
const toast = useToast()

const monthKey = ref(currentMonthKey())
const employeeFilter = ref('')
const workTypeFilter = ref('')
const exporting = ref(false)

const engineers = computed(() => {
  void dataVersion.value
  return listEngineers()
})

const workTypes = computed(() => {
  void dataVersion.value
  return listWorkTypes()
})

const report = computed(() => {
  void dataVersion.value
  return buildMonthlyReport({
    monthKey: monthKey.value,
    employeeId: employeeFilter.value || undefined,
    workTypeId: workTypeFilter.value || undefined,
  })
})

const hasFilter = computed(() => !!employeeFilter.value || !!workTypeFilter.value)

function resetFilters(): void {
  employeeFilter.value = ''
  workTypeFilter.value = ''
}

/* ---------------- 下钻明细 ---------------- */
const drillEmployeeId = ref<string | null>(null)

const drillRecords = computed(() => {
  void dataVersion.value
  if (!drillEmployeeId.value) return []
  return report.value.approvedRecords.filter((r) => r.employeeId === drillEmployeeId.value)
})

const drillName = computed(() =>
  drillEmployeeId.value ? getEmployeeName(drillEmployeeId.value) : '',
)

function openDrill(employeeId: string): void {
  drillEmployeeId.value = employeeId
}

/* ---------------- 明细表（全部） ---------------- */
const detailOpen = ref(false)

/* ---------------- 导出 ---------------- */
function doExport(): void {
  if (exporting.value) return
  const r = report.value

  if (r.approvedRecords.length === 0) {
    toast.error('本月没有已确认记录，无法导出')
    return
  }

  exporting.value = true
  try {
    const workTypeMap = new Map<string, WorkType>(workTypes.value.map((w) => [w.id, w]))
    const employeeNameMap = new Map<string, string>(
      engineers.value.map((e) => [e.id, e.name]),
    )

    const result = exportMonthlyReport({
      report: r,
      records: r.approvedRecords,
      workTypeMap,
      employeeNameMap,
    })

    toast.success(`已导出 ${result.rows} 条明细：${result.filename}`)
  } catch (err) {
    console.error(err)
    toast.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

function modeLabel(record: WorkRecord): string {
  const wt = getWorkType(record.workTypeId)
  return wt?.billingMode === 'BY_HOUR' ? '按小时' : '按次'
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-head__title">月度报表</h1>
        <p class="page-head__desc">
          {{ formatMonthLabel(monthKey) }} · 仅「已确认」记录计入核算
        </p>
      </div>
      <div class="page-head__actions">
        <MonthSwitcher v-model="monthKey" />
        <button
          type="button"
          class="btn btn--primary btn--sm"
          :disabled="exporting || report.approvedRecords.length === 0"
          @click="doExport"
        >
          <AppIcon :name="exporting ? 'spinner' : 'download'" :size="15" :class="{ spin: exporting }" />
          {{ exporting ? '导出中…' : '导出 Excel' }}
        </button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar report-filters">
      <select v-model="employeeFilter" class="select report-select" aria-label="按员工筛选">
        <option value="">全部员工</option>
        <option v-for="emp in engineers" :key="emp.id" :value="emp.id">{{ emp.name }}</option>
      </select>
      <select v-model="workTypeFilter" class="select report-select" aria-label="按工作类型筛选">
        <option value="">全部类型</option>
        <option v-for="wt in workTypes" :key="wt.id" :value="wt.id">{{ wt.name }}</option>
      </select>
      <button v-if="hasFilter" type="button" class="btn btn--ghost btn--sm" @click="resetFilters">
        <AppIcon name="x" :size="14" />
        清除
      </button>
    </div>

    <!-- 未计入提示（STA-03） -->
    <div v-if="report.pendingCount > 0 || report.rejectedCount > 0" class="alert alert--warning report-alert">
      <AppIcon name="warning" :size="16" />
      <span>
        本月另有
        <strong v-if="report.pendingCount > 0">{{ report.pendingCount }} 条待确认</strong>
        <template v-if="report.pendingCount > 0 && report.rejectedCount > 0">、</template>
        <strong v-if="report.rejectedCount > 0">{{ report.rejectedCount }} 条已驳回</strong>
        记录未计入核算，请及时处理以免漏算
      </span>
    </div>

    <!-- 汇总卡片 -->
    <section class="metrics" aria-label="本月核算指标">
      <div class="metrics__lead">
        <span class="metrics__lead-label">提成总额</span>
        <p class="metrics__lead-value">
          <em>¥</em>{{ formatMoneyShort(report.summary.totalAmount) }}
        </p>
        <span class="metrics__lead-foot">{{ report.approvedRecords.length }} 条已确认计入</span>
      </div>

      <div class="metrics__grid">
        <div class="metrics__cell">
          <span class="metrics__cell-k">按次次数</span>
          <span class="metrics__cell-v">{{ report.summary.countTotal }}<em>次</em></span>
          <span class="metrics__cell-f">¥{{ formatMoneyShort(report.summary.byCountAmount) }}</span>
        </div>
        <div class="metrics__cell">
          <span class="metrics__cell-k">正常时长</span>
          <span class="metrics__cell-v">{{ formatHours(report.summary.normalHoursTotal) }}</span>
          <span class="metrics__cell-f">按时计费</span>
        </div>
        <div class="metrics__cell">
          <span class="metrics__cell-k">加班时长</span>
          <span class="metrics__cell-v metrics__cell-v--warn">
            {{ formatHours(report.summary.overtimeHoursTotal) }}
          </span>
          <span class="metrics__cell-f">已按倍率</span>
        </div>
        <div class="metrics__cell">
          <span class="metrics__cell-k">按时金额</span>
          <span class="metrics__cell-v">¥{{ formatMoneyShort(report.summary.byHourAmount) }}</span>
          <span class="metrics__cell-f">含加班</span>
        </div>
      </div>
    </section>

    <!-- 按员工汇总 -->
    <section class="report-section">
      <div class="section-title">
        <span>按员工汇总</span>
        <span class="text-xs text-tertiary">按提成金额排序 · 点击下钻明细</span>
      </div>

      <!-- 移动端：卡片式汇总（避免横向滚动表格） -->
      <div v-if="report.byEmployee.length > 0" class="emp-cards">
        <button
          v-for="item in report.byEmployee"
          :key="item.employeeId"
          type="button"
          class="emp-card"
          @click="openDrill(item.employeeId)"
        >
          <div class="emp-card__head">
            <span class="report-emp">
              <span class="report-emp__avatar">{{ item.employeeName.slice(0, 1) }}</span>
              <span class="fw-600">{{ item.employeeName }}</span>
            </span>
            <span class="emp-card__amount tabular">
              ¥{{ formatMoneyShort(item.summary.totalAmount) }}
            </span>
          </div>
          <div class="emp-card__grid">
            <span><em>次数</em>{{ item.summary.countTotal }}</span>
            <span><em>正常</em>{{ formatHours(item.summary.normalHoursTotal) }}</span>
            <span><em>加班</em>{{ formatHours(item.summary.overtimeHoursTotal) }}</span>
            <span><em>按次</em>¥{{ formatMoneyShort(item.summary.byCountAmount) }}</span>
            <span><em>按时</em>¥{{ formatMoneyShort(item.summary.byHourAmount) }}</span>
          </div>
          <AppIcon name="chevron-right" :size="16" class="text-tertiary emp-card__arrow" />
        </button>

        <div class="emp-card emp-card--total">
          <div class="emp-card__head">
            <span class="fw-600">合计</span>
            <span class="emp-card__amount tabular">
              ¥{{ formatMoneyShort(report.summary.totalAmount) }}
            </span>
          </div>
          <div class="emp-card__grid">
            <span><em>次数</em>{{ report.summary.countTotal }}</span>
            <span><em>正常</em>{{ formatHours(report.summary.normalHoursTotal) }}</span>
            <span><em>加班</em>{{ formatHours(report.summary.overtimeHoursTotal) }}</span>
            <span><em>按次</em>¥{{ formatMoneyShort(report.summary.byCountAmount) }}</span>
            <span><em>按时</em>¥{{ formatMoneyShort(report.summary.byHourAmount) }}</span>
          </div>
        </div>
      </div>

      <!-- 桌面端：表格 -->
      <div v-else-if="report.byEmployee.length === 0" class="card">
        <EmptyState
          icon="chart"
          title="本月暂无已确认记录"
          desc="确认记录后，这里会按员工汇总提成金额"
        />
      </div>

      <div v-if="report.byEmployee.length > 0" class="table-wrap report-table-desktop">
        <table class="table">
          <thead>
            <tr>
              <th>员工</th>
              <th class="num">次数</th>
              <th class="num">正常时长</th>
              <th class="num">加班时长</th>
              <th class="num">按次金额</th>
              <th class="num">按时金额</th>
              <th class="num">提成合计</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in report.byEmployee"
              :key="item.employeeId"
              class="report-row"
              @click="openDrill(item.employeeId)"
            >
              <td>
                <span class="report-emp">
                  <span class="report-emp__avatar">{{ item.employeeName.slice(0, 1) }}</span>
                  {{ item.employeeName }}
                </span>
              </td>
              <td class="num tabular">{{ item.summary.countTotal }}</td>
              <td class="num tabular">{{ formatHours(item.summary.normalHoursTotal) }}</td>
              <td class="num tabular">{{ formatHours(item.summary.overtimeHoursTotal) }}</td>
              <td class="num tabular">¥{{ formatMoneyShort(item.summary.byCountAmount) }}</td>
              <td class="num tabular">¥{{ formatMoneyShort(item.summary.byHourAmount) }}</td>
              <td class="num tabular fw-700">¥{{ formatMoneyShort(item.summary.totalAmount) }}</td>
              <td class="num">
                <AppIcon name="chevron-right" :size="16" class="text-tertiary" />
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="report-total">
              <td>合计</td>
              <td class="num tabular">{{ report.summary.countTotal }}</td>
              <td class="num tabular">{{ formatHours(report.summary.normalHoursTotal) }}</td>
              <td class="num tabular">{{ formatHours(report.summary.overtimeHoursTotal) }}</td>
              <td class="num tabular">¥{{ formatMoneyShort(report.summary.byCountAmount) }}</td>
              <td class="num tabular">¥{{ formatMoneyShort(report.summary.byHourAmount) }}</td>
              <td class="num tabular">¥{{ formatMoneyShort(report.summary.totalAmount) }}</td>
              <td></td>
            </tr>
          </tfoot>
        </table>
      </div>
    </section>

    <!-- 按类型汇总 -->
    <section v-if="report.byWorkType.length > 0" class="report-section">
      <div class="section-title">
        <span>按工作类型汇总</span>
      </div>
      <div class="type-summary">
        <div v-for="item in report.byWorkType" :key="item.workTypeId" class="type-summary__card">
          <div class="row row--between">
            <span class="fw-600 text-sm">{{ item.workTypeName }}</span>
            <span class="tag" :class="item.billingMode === 'BY_HOUR' ? 'tag--hour' : 'tag--count'">
              {{ item.billingMode === 'BY_HOUR' ? '按小时' : '按次' }}
            </span>
          </div>
          <p class="type-summary__amount tabular">¥{{ formatMoneyShort(item.summary.totalAmount) }}</p>
          <p class="type-summary__meta tabular">
            {{ item.summary.recordCount }} 条
            <template v-if="item.billingMode === 'BY_HOUR'">
              · 正常 {{ formatHours(item.summary.normalHoursTotal) }} · 加班
              {{ formatHours(item.summary.overtimeHoursTotal) }}
            </template>
            <template v-else> · {{ item.summary.countTotal }} 次</template>
          </p>
        </div>
      </div>
    </section>

    <!-- 明细入口 -->
    <section class="report-section">
      <div class="section-title">
        <span>核算明细</span>
        <button
          type="button"
          class="btn btn--ghost btn--sm"
          :disabled="report.approvedRecords.length === 0"
          @click="detailOpen = true"
        >
          查看全部 {{ report.approvedRecords.length }} 条
          <AppIcon name="chevron-right" :size="14" />
        </button>
      </div>

      <div v-if="report.approvedRecords.length === 0" class="card">
        <EmptyState icon="file-text" title="暂无核算明细" desc="确认记录后这里会显示每一条的核算结果" />
      </div>

      <div v-else class="table-wrap detail-table-desktop">
        <table class="table">
          <thead>
            <tr>
              <th>日期</th>
              <th>员工</th>
              <th>类型</th>
              <th>计费</th>
              <th>时间</th>
              <th class="num">正常</th>
              <th class="num">加班</th>
              <th class="num">单价</th>
              <th class="num">金额</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in report.approvedRecords.slice(0, 10)" :key="record.id">
              <td class="tabular">{{ formatDateLabel(record.serviceDate) }}</td>
              <td>{{ getEmployeeName(record.employeeId) }}</td>
              <td>{{ getWorkTypeName(record.workTypeId) }}</td>
              <td>
                <span class="tag" :class="modeLabel(record) === '按小时' ? 'tag--hour' : 'tag--count'">
                  {{ modeLabel(record) }}
                </span>
              </td>
              <td class="tabular">
                {{ record.startTime }}<template v-if="record.endTime">~{{ record.endTime }}</template>
              </td>
              <td class="num tabular">
                {{ computeRecordSafe(record).billingMode === 'BY_HOUR' ? formatHours(computeRecordSafe(record).normalHours) : '—' }}
              </td>
              <td class="num tabular">
                {{ computeRecordSafe(record).billingMode === 'BY_HOUR' ? formatHours(computeRecordSafe(record).overtimeHours) : '—' }}
              </td>
              <td class="num tabular">¥{{ formatMoneyShort(record.billingSnapshot.unitPrice) }}</td>
              <td class="num tabular fw-600">¥{{ formatMoneyShort(computeRecordSafe(record).total) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="report.approvedRecords.length > 10" class="text-xs text-tertiary mt-sm">
        仅显示前 10 条，完整明细请导出 Excel 或点击「查看全部」
      </p>

      <!-- 移动端明细：卡片式（避免横向滚动表格） -->
      <div v-if="report.approvedRecords.length > 0" class="detail-cards">
        <button
          v-for="record in report.approvedRecords.slice(0, 10)"
          :key="record.id"
          type="button"
          class="detail-card"
          @click="detailOpen = true"
        >
          <div class="detail-card__head">
            <span class="fw-600 text-sm">
              {{ getEmployeeName(record.employeeId) }} · {{ getWorkTypeName(record.workTypeId) }}
            </span>
            <span class="fw-700 tabular">¥{{ formatMoneyShort(computeRecordSafe(record).total) }}</span>
          </div>
          <div class="detail-card__meta">
            <span>{{ formatDateLabel(record.serviceDate) }}</span>
            <span class="tabular">
              {{ record.startTime }}<template v-if="record.endTime">~{{ record.endTime }}</template>
            </span>
            <span class="tag" :class="modeLabel(record) === '按小时' ? 'tag--hour' : 'tag--count'">
              {{ modeLabel(record) }}
            </span>
            <span v-if="record.isBackfill" class="flag-label flag-label--backfill">补录</span>
          </div>
          <div v-if="computeRecordSafe(record).billingMode === 'BY_HOUR'" class="detail-card__hours tabular">
            正常 {{ formatHours(computeRecordSafe(record).normalHours) }} · 加班
            {{ formatHours(computeRecordSafe(record).overtimeHours) }} · 单价 ¥{{
              formatMoneyShort(record.billingSnapshot.unitPrice)
            }}
          </div>
          <div v-else class="detail-card__hours tabular">
            单价 ¥{{ formatMoneyShort(record.billingSnapshot.unitPrice) }}/次
          </div>
        </button>
      </div>
    </section>

    <!-- 下钻明细 -->
    <AppSheet
      :open="!!drillEmployeeId"
      :title="`${drillName} · 核算明细`"
      @close="drillEmployeeId = null"
    >
      <div class="drill-list">
        <div v-for="record in drillRecords" :key="record.id" class="drill-item">
          <div class="row row--between">
            <span class="fw-600 text-sm">{{ getWorkTypeName(record.workTypeId) }}</span>
            <span class="fw-700 tabular">¥{{ formatMoney(computeRecordSafe(record).total) }}</span>
          </div>
          <p class="text-xs text-tertiary">
            {{ formatDateLabel(record.serviceDate) }} {{ weekdayLabel(record.serviceDate) }} ·
            {{ record.startTime }}<template v-if="record.endTime">~{{ record.endTime }}</template>
            · {{ record.location }}
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

    <!-- 全部明细 -->
    <AppSheet :open="detailOpen" title="核算明细（全部）" @close="detailOpen = false">
      <div class="drill-list">
        <div v-for="record in report.approvedRecords" :key="record.id" class="drill-item">
          <div class="row row--between">
            <span class="fw-600 text-sm">
              {{ getEmployeeName(record.employeeId) }} · {{ getWorkTypeName(record.workTypeId) }}
            </span>
            <span class="fw-700 tabular">¥{{ formatMoney(computeRecordSafe(record).total) }}</span>
          </div>
          <p class="text-xs text-tertiary">
            {{ formatDateLabel(record.serviceDate) }} · {{ record.startTime
            }}<template v-if="record.endTime">~{{ record.endTime }}</template>
            <span v-if="record.isBackfill" class="flag-label flag-label--backfill" style="margin-left: 4px">补录</span>
          </p>
          <p class="drill-item__explain">{{ computeRecordSafe(record).explanation }}</p>
        </div>
      </div>
      <template #footer>
        <button type="button" class="btn btn--secondary btn--block" @click="detailOpen = false">
          关闭
        </button>
      </template>
    </AppSheet>
  </div>
</template>

<style scoped>
/* ---------- 指标区：主数字 + 细分隔条 ---------- */
.metrics {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-xs);
  margin-bottom: var(--space-md);
}

@media (min-width: 900px) {
  .metrics {
    grid-template-columns: minmax(240px, 0.9fr) 2.4fr;
  }
}

.metrics__lead {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-md) var(--space-lg);
  background:
    radial-gradient(120% 100% at 0% 0%, rgba(56, 189, 248, 0.16), transparent 60%),
    linear-gradient(160deg, #0b1220 0%, #16294c 100%);
  color: #fff;
}

.metrics__lead-label {
  font-size: var(--text-2xs);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wider);
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}

.metrics__lead-value {
  font-size: var(--text-3xl);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-tight);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  margin-top: 2px;
}

.metrics__lead-value em {
  font-style: normal;
  font-size: var(--text-md);
  font-weight: var(--weight-medium);
  opacity: 0.6;
  margin-right: 2px;
}

.metrics__lead-foot {
  font-size: var(--text-2xs);
  color: rgba(255, 255, 255, 0.55);
}

.metrics__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (min-width: 640px) {
  .metrics__grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

.metrics__cell {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: var(--space-md);
  border-left: 1px solid var(--color-divider);
  border-top: 1px solid var(--color-divider);
  min-width: 0;
}

@media (min-width: 640px) {
  .metrics__cell {
    border-top: 0;
  }
}

.metrics__cell-k {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.metrics__cell-v {
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-snug);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.metrics__cell-v em {
  font-style: normal;
  font-size: var(--text-xs);
  font-weight: var(--weight-normal);
  color: var(--color-fg-tertiary);
  margin-left: 1px;
}

.metrics__cell-v--warn {
  color: var(--color-warning);
}

.metrics__cell-f {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
}
.report-filters {
  margin-bottom: var(--space-md);
}

.report-select {
  flex: 0 1 180px;
  min-width: 130px;
}

.report-alert {
  margin-bottom: var(--space-md);
}

.report-section {
  margin-top: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.report-row {
  cursor: pointer;
}

/* 移动端卡片式汇总：默认显示，桌面隐藏 */
.emp-cards {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.emp-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding: var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  text-align: left;
  color: inherit;
  transition: border-color var(--duration-fast) var(--ease-in-out);
}

.emp-card:hover {
  border-color: var(--color-border-strong);
}

.emp-card--total {
  background: var(--color-surface-muted);
  border-color: var(--color-border-strong);
}

.emp-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-right: 20px;
}

.emp-card__amount {
  font-size: var(--text-lg);
  font-weight: 700;
  letter-spacing: -0.01em;
}

.emp-card__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px var(--space-sm);
  font-size: var(--text-xs);
  color: var(--color-fg-secondary);
  font-variant-numeric: tabular-nums;
}

.emp-card__grid em {
  display: block;
  font-style: normal;
  font-size: 10px;
  color: var(--color-fg-tertiary);
}

.emp-card__arrow {
  position: absolute;
  right: var(--space-sm);
  top: 50%;
  transform: translateY(-50%);
}

/* 桌面端表格：默认隐藏，≥768px 显示 */
.report-table-desktop {
  display: none;
}

.detail-table-desktop {
  display: none;
}

.detail-cards {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.detail-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  padding: var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  text-align: left;
  color: inherit;
  transition: border-color var(--duration-fast) var(--ease-in-out);
}

.detail-card:hover {
  border-color: var(--color-border-strong);
}

.detail-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.detail-card__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-sm);
  font-size: var(--text-xs);
  color: var(--color-fg-tertiary);
}

.detail-card__hours {
  font-size: 11px;
  color: var(--color-fg-secondary);
  padding-top: 6px;
  border-top: 1px dashed var(--color-divider);
}

@media (min-width: 768px) {
  .emp-cards {
    display: none;
  }
  .detail-cards {
    display: none;
  }
  .report-table-desktop {
    display: block;
  }
  .detail-table-desktop {
    display: block;
  }
}

.report-emp {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.report-emp__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-surface-sunken);
  color: var(--color-fg-secondary);
  font-size: 11px;
  font-weight: 700;
}

.report-total td {
  background: var(--color-surface-muted);
  font-weight: 700;
  border-top: 2px solid var(--color-border);
}

.type-summary {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--space-sm);
}

.type-summary__card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.type-summary__amount {
  font-size: var(--text-xl);
  font-weight: 700;
  letter-spacing: -0.02em;
  margin-top: 2px;
}

.type-summary__meta {
  font-size: 11px;
  color: var(--color-fg-tertiary);
}

.drill-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.drill-item {
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.drill-item__explain {
  font-size: 11px;
  color: var(--color-fg-tertiary);
  line-height: var(--leading-relaxed);
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px dashed var(--color-divider);
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
