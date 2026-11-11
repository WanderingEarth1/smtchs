<template>
  <div class="smt-dash">
    <!-- 页头 -->
    <header class="smt-dash__head">
      <div class="smt-dash__head-main">
        <p class="smt-dash__eyebrow">OVERVIEW</p>
        <h1 class="smt-dash__title">经营概览</h1>
        <p class="smt-dash__sub">
          {{ monthLabel }}
          <span class="smt-dot">·</span>
          在岗工程师 {{ activeCount }} 人
          <span class="smt-dot">·</span>
          本月 {{ totalRecords }} 条记录
        </p>
      </div>
      <div class="smt-dash__head-actions">
        <el-button size="small" @click="go('/smtchs/report')">
          <el-icon><Document /></el-icon>
          报表
        </el-button>
        <el-button type="primary" size="small" @click="go('/smtchs/review')">
          <el-icon><CircleCheck /></el-icon>
          去审核
          <span v-if="report.pendingCount > 0" class="smt-dash__badge">{{ report.pendingCount }}</span>
        </el-button>
      </div>
    </header>

    <!-- 指标区：主数字 + 细分隔条 -->
    <section class="smt-metrics" aria-label="本月核心指标">
      <div class="smt-metrics__lead">
        <span class="smt-metrics__lead-label">本月提成总额</span>
        <p class="smt-metrics__lead-value">
          <em>¥</em>{{ formatMoney(report.summary.totalAmount) }}
        </p>
        <span class="smt-metrics__lead-foot">
          已确认 {{ report.approvedRecords.length }} 条计入核算
        </span>
      </div>

      <div class="smt-metrics__grid">
        <div class="smt-metrics__cell">
          <span class="smt-metrics__cell-k">待确认</span>
          <span class="smt-metrics__cell-v" :class="{ 'is-warn': report.pendingCount > 0 }">
            {{ report.pendingCount }}<em>条</em>
          </span>
          <span class="smt-metrics__cell-f">未计入核算</span>
        </div>
        <div class="smt-metrics__cell">
          <span class="smt-metrics__cell-k">正常时长</span>
          <span class="smt-metrics__cell-v">{{ formatHours(report.summary.normalHoursTotal) }}</span>
          <span class="smt-metrics__cell-f">按时计费</span>
        </div>
        <div class="smt-metrics__cell">
          <span class="smt-metrics__cell-k">加班时长</span>
          <span class="smt-metrics__cell-v is-warn">{{ formatHours(report.summary.overtimeHoursTotal) }}</span>
          <span class="smt-metrics__cell-f">已按倍率</span>
        </div>
        <div class="smt-metrics__cell">
          <span class="smt-metrics__cell-k">审核完成率</span>
          <span class="smt-metrics__cell-v">{{ approveRate }}<em>%</em></span>
          <span class="smt-metrics__cell-f">驳回 {{ report.rejectedCount }} 条</span>
        </div>
      </div>
    </section>

    <!-- 待处理提示 -->
    <div v-if="report.pendingCount > 0 || report.rejectedCount > 0" class="smt-alerts">
      <button
        v-if="report.pendingCount > 0"
        type="button"
        class="smt-alert-row is-warning"
        @click="go('/smtchs/review')"
      >
        <span class="smt-alert-row__dot" aria-hidden="true" />
        <span class="smt-alert-row__text">
          <strong>{{ report.pendingCount }} 条待确认</strong>
          <span>未计入本月核算，请及时处理</span>
        </span>
        <el-icon class="smt-alert-row__arrow"><ArrowRight /></el-icon>
      </button>
      <button
        v-if="report.rejectedCount > 0"
        type="button"
        class="smt-alert-row is-danger"
        @click="go('/smtchs/review')"
      >
        <span class="smt-alert-row__dot" aria-hidden="true" />
        <span class="smt-alert-row__text">
          <strong>{{ report.rejectedCount }} 条已驳回</strong>
          <span>等待员工修改后重新提交</span>
        </span>
        <el-icon class="smt-alert-row__arrow"><ArrowRight /></el-icon>
      </button>
    </div>

    <!-- 主体：非对称双栏 -->
    <div class="smt-dash__body">
      <div class="smt-dash__col smt-dash__col--main">
        <!-- 待确认记录 -->
        <section class="smt-panel">
          <div class="smt-sec-head">
            <div class="smt-sec-head__main">
              <span class="smt-sec-head__title">待确认记录</span>
              <span class="smt-sec-head__sub">确认后锁定并计入提成</span>
            </div>
            <button type="button" class="smt-panel__more" @click="go('/smtchs/review')">
              全部
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>

          <div v-if="pendingRecords.length === 0" class="smt-panel__empty">
            <span class="smt-panel__empty-text">没有待确认记录，所有记录都已处理完毕</span>
          </div>
          <ul v-else class="smt-list">
            <li v-for="r in pendingRecords" :key="r.id" class="smt-list__item">
              <div class="smt-list__main">
                <span class="smt-list__title">{{ getWorkTypeName(r.workTypeId) }}</span>
                <span class="smt-list__meta">
                  {{ getEmployeeName(r.employeeId) }}
                  <span class="smt-dot">·</span>
                  {{ formatDateLabel(r.serviceDate) }} {{ r.startTime }}
                  <span class="smt-dot">·</span>
                  {{ r.location }}
                </span>
              </div>
              <el-button link type="primary" size="small" @click="go('/smtchs/review')">
                去确认
              </el-button>
            </li>
          </ul>
        </section>

        <!-- 员工排行 -->
        <section class="smt-panel">
          <div class="smt-sec-head">
            <div class="smt-sec-head__main">
              <span class="smt-sec-head__title">提成排行</span>
              <span class="smt-sec-head__sub">按本月已确认金额</span>
            </div>
            <button type="button" class="smt-panel__more" @click="go('/smtchs/report')">
              报表
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>

          <div v-if="report.byEmployee.length === 0" class="smt-panel__empty">
            <span class="smt-panel__empty-text">本月暂无已确认记录</span>
          </div>
          <ol v-else class="smt-rank">
            <li v-for="(e, i) in report.byEmployee" :key="e.employeeId" class="smt-rank__row">
              <span class="smt-rank__no" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
              <span class="smt-rank__name">{{ e.employeeName }}</span>
              <span class="smt-rank__bar">
                <span
                  class="smt-rank__bar-fill"
                  :style="{ width: barWidth(e.summary.totalAmount) }"
                />
              </span>
              <span class="smt-rank__amount">¥{{ formatMoney(e.summary.totalAmount) }}</span>
            </li>
          </ol>
        </section>
      </div>

      <div class="smt-dash__col smt-dash__col--side">
        <!-- 类型分布 -->
        <section class="smt-panel">
          <div class="smt-sec-head">
            <div class="smt-sec-head__main">
              <span class="smt-sec-head__title">类型分布</span>
              <span class="smt-sec-head__sub">按工作类型金额</span>
            </div>
          </div>

          <div v-if="report.byWorkType.length === 0" class="smt-panel__empty">
            <span class="smt-panel__empty-text">暂无数据</span>
          </div>
          <ul v-else class="smt-dist">
            <li v-for="t in report.byWorkType" :key="t.workTypeId" class="smt-dist__row">
              <div class="smt-dist__head">
                <span class="smt-dist__name">{{ t.workTypeName }}</span>
                <el-tag :type="t.billingMode === 'BY_HOUR' ? 'warning' : 'success'" size="small">
                  {{ t.billingMode === 'BY_HOUR' ? '按小时' : '按次' }}
                </el-tag>
              </div>
              <div class="smt-dist__bar">
                <span
                  class="smt-dist__bar-fill"
                  :style="{ width: barWidth(t.summary.totalAmount) }"
                />
              </div>
              <div class="smt-dist__foot">
                <span>¥{{ formatMoney(t.summary.totalAmount) }}</span>
                <span class="smt-dist__count">{{ t.summary.recordCount }} 条</span>
              </div>
            </li>
          </ul>
        </section>

        <!-- 最近提交 -->
        <section class="smt-panel">
          <div class="smt-sec-head">
            <div class="smt-sec-head__main">
              <span class="smt-sec-head__title">最近提交</span>
              <span class="smt-sec-head__sub">最新 5 条</span>
            </div>
            <button type="button" class="smt-panel__more" @click="go('/smtchs/record')">
              全部
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>

          <div v-if="recentSubmissions.length === 0" class="smt-panel__empty">
            <span class="smt-panel__empty-text">暂无记录</span>
          </div>
          <ul v-else class="smt-list">
            <li v-for="r in recentSubmissions" :key="r.id" class="smt-list__item">
              <div class="smt-list__main">
                <span class="smt-list__title">
                  {{ getEmployeeName(r.employeeId) }}
                  <el-tag :type="statusType(r.status)" size="small">{{ statusText(r.status) }}</el-tag>
                </span>
                <span class="smt-list__meta">
                  {{ getWorkTypeName(r.workTypeId) }}
                  <span class="smt-dot">·</span>
                  {{ formatDateLabel(r.serviceDate) }}
                </span>
              </div>
            </li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup name="SmtDashboard">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, CircleCheck, Document } from '@element-plus/icons-vue'
import { getMonthlyReport } from '@/api/smtchs/report'
import { listEmployee } from '@/api/smtchs/employee'
import { listRecord } from '@/api/smtchs/record'

const router = useRouter()

const now = new Date()
const monthKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`

const emptySummary = {
  recordCount: 0,
  countTotal: 0,
  normalHoursTotal: 0,
  overtimeHoursTotal: 0,
  byCountAmount: 0,
  byHourAmount: 0,
  totalAmount: 0,
}

const report = ref({
  monthKey,
  summary: { ...emptySummary },
  byEmployee: [],
  byWorkType: [],
  approvedRecords: [],
  pendingCount: 0,
  rejectedCount: 0,
})

const engineers = ref([])
const pendingRecords = ref([])
const recentSubmissions = ref([])
const totalRecords = ref(0)
const loading = ref(false)

const monthLabel = computed(() => {
  const [y, m] = monthKey.split('-')
  return `${y}年${Number(m)}月`
})

const activeCount = computed(() => engineers.value.filter((e) => e.status === '0').length)

const approveRate = computed(() => {
  if (totalRecords.value === 0) return 0
  return Math.round((report.value.approvedRecords.length / totalRecords.value) * 100)
})

const maxAmount = computed(() => {
  const all = [
    ...report.value.byEmployee.map((e) => Number(e.summary.totalAmount) || 0),
    ...report.value.byWorkType.map((t) => Number(t.summary.totalAmount) || 0),
  ]
  return Math.max(1, ...all)
})

function barWidth(amount) {
  const v = Number(amount) || 0
  return `${Math.max(2, Math.round((v / maxAmount.value) * 100))}%`
}

function formatMoney(v) {
  const n = Number(v) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatHours(v) {
  const h = Number(v) || 0
  if (h === 0) return '0h'
  const totalMin = Math.round(h * 60)
  const hh = Math.floor(totalMin / 60)
  const mm = totalMin % 60
  if (hh === 0) return `${mm}min`
  if (mm === 0) return `${hh}h`
  return `${hh}h${mm}m`
}

function formatDateLabel(d) {
  if (!d) return ''
  const [, m, day] = d.split('-')
  return `${Number(m)}月${Number(day)}日`
}

function statusText(s) {
  return { PENDING: '待确认', APPROVED: '已确认', REJECTED: '已驳回' }[s] || s
}

function statusType(s) {
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] || 'info'
}

function getEmployeeName(id) {
  const e = engineers.value.find((x) => String(x.id) === String(id))
  return e ? e.name : '未知员工'
}

function getWorkTypeName(id) {
  const t = report.value.byWorkType.find((x) => String(x.workTypeId) === String(id))
  if (t) return t.workTypeName
  return '未知类型'
}

function go(path) {
  router.push(path)
}

async function loadData() {
  loading.value = true
  try {
    const [rep, emps, pending, all] = await Promise.all([
      getMonthlyReport({ month: monthKey }),
      listEmployee({ pageNum: 1, pageSize: 999 }),
      listRecord({ pageNum: 1, pageSize: 5, month: monthKey, status: 'PENDING' }),
      listRecord({ pageNum: 1, pageSize: 999, month: monthKey }),
    ])
    report.value = rep.data || report.value
    engineers.value = emps.rows || []
    pendingRecords.value = pending.rows || []
    totalRecords.value = all.total || 0
    recentSubmissions.value = [...(all.rows || [])]
      .sort((a, b) => String(b.submittedAt || '').localeCompare(String(a.submittedAt || '')))
      .slice(0, 5)
  } catch (e) {
    // 静默失败，页面显示空态
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.smt-dash {
  display: flex;
  flex-direction: column;
  gap: var(--smt-gap-md);
  /* 若依 app-main 默认无内边距，这里补上，避免内容贴边 */
  padding: var(--smt-gap-md) 20px var(--smt-gap-lg);
}

/* ---------- 页头 ---------- */
.smt-dash__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.smt-dash__head-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.smt-dash__eyebrow {
  margin: 0;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--el-text-color-secondary);
}

.smt-dash__title {
  margin: 2px 0 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.15;
  color: var(--el-text-color-primary);
}

.smt-dash__sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.smt-dash__head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.smt-dash__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  margin-left: 4px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.24);
  font-size: 11px;
  font-weight: 700;
}

.smt-dot {
  margin: 0 4px;
  opacity: 0.45;
}

/* ---------- 指标区 ---------- */
.smt-metrics {
  display: grid;
  grid-template-columns: 1fr;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--smt-radius-lg);
  overflow: hidden;
  box-shadow: var(--smt-shadow-xs);
}

@media (min-width: 900px) {
  .smt-metrics {
    grid-template-columns: minmax(240px, 0.9fr) 2.4fr;
  }
}

.smt-metrics__lead {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 16px 20px;
  background:
    radial-gradient(120% 100% at 0% 0%, rgba(56, 189, 248, 0.18), transparent 60%),
    linear-gradient(160deg, #0b1220 0%, #16294c 100%);
  color: #fff;
}

.smt-metrics__lead-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}

.smt-metrics__lead-value {
  margin: 2px 0 0;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.smt-metrics__lead-value em {
  font-style: normal;
  font-size: 15px;
  font-weight: 500;
  opacity: 0.6;
  margin-right: 2px;
}

.smt-metrics__lead-foot {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.55);
}

.smt-metrics__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (min-width: 1200px) {
  .smt-metrics__grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

.smt-metrics__cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 14px 18px;
  border-left: 1px solid var(--el-border-color-lighter);
  border-top: 1px solid var(--el-border-color-lighter);
}

.smt-metrics__cell-k {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--el-text-color-secondary);
}

.smt-metrics__cell-v {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.smt-metrics__cell-v.is-warn {
  color: var(--el-color-warning);
}

.smt-metrics__cell-v em {
  font-style: normal;
  font-size: 12px;
  font-weight: 500;
  margin-left: 2px;
  opacity: 0.6;
}

.smt-metrics__cell-f {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

/* ---------- 待处理提示 ---------- */
.smt-alerts {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.smt-alert-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 14px;
  border: 1px solid transparent;
  border-radius: var(--smt-radius-md);
  background: var(--el-fill-color-light);
  cursor: pointer;
  text-align: left;
  transition: background 0.15s ease;
}

.smt-alert-row:hover {
  background: var(--el-fill-color);
}

.smt-alert-row.is-warning {
  border-color: var(--el-color-warning-light-5);
  background: var(--el-color-warning-light-9);
}

.smt-alert-row.is-danger {
  border-color: var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
}

.smt-alert-row__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
}

.smt-alert-row.is-warning .smt-alert-row__dot {
  background: var(--el-color-warning);
}

.smt-alert-row.is-danger .smt-alert-row__dot {
  background: var(--el-color-danger);
}

.smt-alert-row__text {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.smt-alert-row__text strong {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.smt-alert-row__arrow {
  flex: none;
  color: var(--el-text-color-placeholder);
}

/* ---------- 主体双栏 ---------- */
.smt-dash__body {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

@media (min-width: 1100px) {
  .smt-dash__body {
    grid-template-columns: 1.55fr 1fr;
  }
}

.smt-dash__col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

/* ---------- 面板 ---------- */
.smt-panel {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--smt-radius-lg);
  overflow: hidden;
  box-shadow: var(--smt-shadow-xs);
}

.smt-sec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.smt-sec-head__main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.smt-sec-head__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.smt-sec-head__sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.smt-panel__more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 4px;
  border: none;
  background: none;
  font-size: 12px;
  color: var(--el-color-primary);
  cursor: pointer;
}

.smt-panel__more:hover {
  text-decoration: underline;
}

.smt-panel__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px 16px;
}

.smt-panel__empty-text {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

/* ---------- 列表 ---------- */
.smt-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.smt-list__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.smt-list__item:last-child {
  border-bottom: none;
}

.smt-list__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.smt-list__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.smt-list__meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 排行 ---------- */
.smt-rank {
  margin: 0;
  padding: 4px 0;
  list-style: none;
}

.smt-rank__row {
  display: grid;
  grid-template-columns: 22px 72px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 7px 16px;
}

.smt-rank__no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--smt-radius-sm);
  background: var(--el-fill-color);
  font-size: 11px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
}

.smt-rank__no.is-top {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.smt-rank__name {
  font-size: 13px;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.smt-rank__bar {
  height: 6px;
  border-radius: 3px;
  background: var(--el-fill-color-light);
  overflow: hidden;
}

.smt-rank__bar-fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--el-color-primary-light-3), var(--el-color-primary));
}

.smt-rank__amount {
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

/* ---------- 类型分布 ---------- */
.smt-dist {
  margin: 0;
  padding: 8px 16px 12px;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.smt-dist__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 5px;
}

.smt-dist__name {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.smt-dist__bar {
  height: 6px;
  border-radius: 3px;
  background: var(--el-fill-color-light);
  overflow: hidden;
}

.smt-dist__bar-fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--el-color-primary-light-3), var(--el-color-primary));
}

.smt-dist__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);
}

.smt-dist__count {
  color: var(--el-text-color-placeholder);
}
</style>
