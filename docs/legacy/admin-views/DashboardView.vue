<script setup lang="ts">
/**
 * 管理端概览
 *
 * 排版策略：
 * - 指标用「一个主数字 + 细分隔条」，不做四宫格卡片
 * - 主体区非对称双栏（1.55fr / 1fr），左侧放待办与动态
 * - 排行用序号 + 比例条，类型分布用横向条形
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuth } from '@/stores/app'
import {
  buildMonthlyReport,
  computeRecordSafe,
  getEmployeeName,
  getWorkTypeName,
  listAllRecords,
  listEngineers,
} from '@/data/service'
import {
  currentMonthKey,
  formatDateLabel,
  formatHours,
  formatMoneyShort,
  formatMonthLabel,
  relativeTime,
} from '@/core/date'

const router = useRouter()
const { dataVersion } = useAuth()

const monthKey = currentMonthKey()

const report = computed(() => {
  void dataVersion.value
  return buildMonthlyReport({ monthKey })
})

const engineers = computed(() => {
  void dataVersion.value
  return listEngineers()
})

const pendingRecords = computed(() => {
  void dataVersion.value
  return listAllRecords({ monthKey, status: 'PENDING' }).slice(0, 5)
})

const recentSubmissions = computed(() => {
  void dataVersion.value
  return [...listAllRecords({ monthKey })]
    .sort((a, b) => (a.submittedAt < b.submittedAt ? 1 : -1))
    .slice(0, 5)
})

const activeCount = computed(() => engineers.value.filter((e) => e.status === 'ACTIVE').length)

const totalRecords = computed(() => {
  void dataVersion.value
  return listAllRecords({ monthKey }).length
})

const approveRate = computed(() => {
  const total = totalRecords.value
  if (total === 0) return 0
  return Math.round((report.value.approvedRecords.length / total) * 100)
})

const maxEmployeeAmount = computed(() =>
  Math.max(1, ...report.value.byEmployee.map((e) => e.summary.totalAmount)),
)

function go(path: string): void {
  void router.push(path)
}
</script>

<template>
  <div class="dash">
    <!-- 页头 -->
    <header class="dash__head">
      <div class="dash__head-main">
        <p class="t-eyebrow">Overview</p>
        <h1 class="dash__title">经营概览</h1>
        <p class="dash__sub">
          {{ formatMonthLabel(monthKey) }}
          <span class="dot-sep">·</span>
          在岗工程师 {{ activeCount }} 人
          <span class="dot-sep">·</span>
          本月 {{ totalRecords }} 条记录
        </p>
      </div>
      <div class="dash__head-actions">
        <button type="button" class="btn btn--secondary btn--sm" @click="go('/a/pc/reports')">
          <AppIcon name="file-text" :size="14" />
          报表
        </button>
        <button type="button" class="btn btn--primary btn--sm" @click="go('/a/pc/review')">
          <AppIcon name="check-circle" :size="14" />
          去审核
          <span v-if="report.pendingCount > 0" class="dash__badge">{{ report.pendingCount }}</span>
        </button>
      </div>
    </header>

    <!-- 指标区：主数字 + 细分隔条 -->
    <section class="metrics" aria-label="本月核心指标">
      <div class="metrics__lead">
        <span class="metrics__lead-label">本月提成总额</span>
        <p class="metrics__lead-value">
          <em>¥</em>{{ formatMoneyShort(report.summary.totalAmount) }}
        </p>
        <span class="metrics__lead-foot">
          已确认 {{ report.approvedRecords.length }} 条计入核算
        </span>
      </div>

      <div class="metrics__grid">
        <div class="metrics__cell">
          <span class="metrics__cell-k">待确认</span>
          <span class="metrics__cell-v" :class="{ 'metrics__cell-v--warn': report.pendingCount > 0 }">
            {{ report.pendingCount }}<em>条</em>
          </span>
          <span class="metrics__cell-f">未计入核算</span>
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
          <span class="metrics__cell-k">审核完成率</span>
          <span class="metrics__cell-v">{{ approveRate }}<em>%</em></span>
          <span class="metrics__cell-f">驳回 {{ report.rejectedCount }} 条</span>
        </div>
      </div>
    </section>

    <!-- 待处理提示：左侧强调条 -->
    <div v-if="report.pendingCount > 0 || report.rejectedCount > 0" class="alerts">
      <button
        v-if="report.pendingCount > 0"
        type="button"
        class="alert-row alert-row--warning"
        @click="go('/a/pc/review')"
      >
        <span class="alert-row__dot" aria-hidden="true" />
        <span class="alert-row__text">
          <strong>{{ report.pendingCount }} 条待确认</strong>
          <span>未计入本月核算，请及时处理</span>
        </span>
        <AppIcon name="chevron-right" :size="15" class="alert-row__arrow" />
      </button>
      <button
        v-if="report.rejectedCount > 0"
        type="button"
        class="alert-row alert-row--danger"
        @click="go('/a/pc/review?status=REJECTED')"
      >
        <span class="alert-row__dot" aria-hidden="true" />
        <span class="alert-row__text">
          <strong>{{ report.rejectedCount }} 条已驳回</strong>
          <span>等待员工修改后重新提交</span>
        </span>
        <AppIcon name="chevron-right" :size="15" class="alert-row__arrow" />
      </button>
    </div>

    <!-- 主体：非对称双栏 -->
    <div class="dash__body">
      <!-- 左栏 -->
      <div class="dash__col dash__col--main">
        <!-- 待审核 -->
        <section class="panel">
          <div class="sec-head">
            <div class="sec-head__main">
              <span class="sec-head__title">待确认记录</span>
              <span class="sec-head__sub">确认后锁定并计入提成</span>
            </div>
            <button type="button" class="panel__more" @click="go('/a/pc/review')">
              全部
              <AppIcon name="chevron-right" :size="13" />
            </button>
          </div>

          <div v-if="pendingRecords.length === 0" class="panel__empty">
            <EmptyState icon="check-circle" title="没有待确认记录" desc="所有记录都已处理完毕" />
          </div>

          <ul v-else class="queue">
            <li v-for="record in pendingRecords" :key="record.id">
              <button type="button" class="queue__row" @click="go(`/a/review/${record.id}`)">
                <span class="queue__avatar">{{ getEmployeeName(record.employeeId).slice(0, 1) }}</span>
                <span class="queue__body">
                  <span class="queue__title">
                    {{ getEmployeeName(record.employeeId) }}
                    <span class="queue__type">{{ getWorkTypeName(record.workTypeId) }}</span>
                    <StatusBadge :status="record.status" :is-backfill="record.isBackfill" />
                  </span>
                  <span class="queue__meta">
                    {{ formatDateLabel(record.serviceDate) }}
                    <span class="dot-sep">·</span>
                    {{ record.startTime }}<template v-if="record.endTime">–{{ record.endTime }}</template>
                    <span class="dot-sep">·</span>
                    {{ record.location }}
                  </span>
                </span>
                <span class="queue__amount tabular">
                  ¥{{ formatMoneyShort(computeRecordSafe(record).total) }}
                </span>
              </button>
            </li>
          </ul>
        </section>

        <!-- 最近提交 -->
        <section class="panel">
          <div class="sec-head">
            <div class="sec-head__main">
              <span class="sec-head__title">最近提交</span>
              <span class="sec-head__sub">按提交时间</span>
            </div>
          </div>
          <ul class="feed">
            <li v-for="record in recentSubmissions" :key="record.id" class="feed__item">
              <span class="feed__dot" aria-hidden="true" />
              <span class="feed__text">
                <strong>{{ getEmployeeName(record.employeeId) }}</strong>
                提交了 {{ getWorkTypeName(record.workTypeId) }}
              </span>
              <span class="feed__time">{{ relativeTime(record.submittedAt) }}</span>
              <span class="feed__amount tabular">
                ¥{{ formatMoneyShort(computeRecordSafe(record).total) }}
              </span>
            </li>
          </ul>
        </section>
      </div>

      <!-- 右栏 -->
      <div class="dash__col dash__col--side">
        <!-- 提成排行 -->
        <section class="panel">
          <div class="sec-head">
            <div class="sec-head__main">
              <span class="sec-head__title">提成排行</span>
              <span class="sec-head__sub">按金额</span>
            </div>
          </div>

          <div v-if="report.byEmployee.length === 0" class="panel__empty">
            <EmptyState icon="chart" title="暂无数据" desc="确认记录后显示排行" />
          </div>

          <ol v-else class="rank">
            <li v-for="(item, idx) in report.byEmployee" :key="item.employeeId" class="rank__item">
              <span class="rank__no">{{ String(idx + 1).padStart(2, '0') }}</span>
              <span class="rank__body">
                <span class="rank__top">
                  <span class="rank__name">{{ item.employeeName }}</span>
                  <span class="rank__amount tabular">
                    ¥{{ formatMoneyShort(item.summary.totalAmount) }}
                  </span>
                </span>
                <span class="rank__bar">
                  <span
                    class="rank__fill"
                    :style="{
                      width: `${Math.max(4, (item.summary.totalAmount / maxEmployeeAmount) * 100)}%`,
                    }"
                  />
                </span>
                <span class="rank__meta tabular">
                  {{ item.summary.countTotal }} 次
                  <span class="dot-sep">·</span>
                  {{ formatHours(item.summary.normalHoursTotal) }}
                  <span class="dot-sep">·</span>
                  加班 {{ formatHours(item.summary.overtimeHoursTotal) }}
                </span>
              </span>
            </li>
          </ol>
        </section>

        <!-- 类型分布 -->
        <section class="panel">
          <div class="sec-head">
            <div class="sec-head__main">
              <span class="sec-head__title">工作类型</span>
              <span class="sec-head__sub">已确认金额</span>
            </div>
          </div>

          <div v-if="report.byWorkType.length === 0" class="panel__empty">
            <EmptyState icon="tag" title="暂无数据" desc="确认记录后显示分布" />
          </div>

          <ul v-else class="types">
            <li v-for="item in report.byWorkType" :key="item.workTypeId" class="types__row">
              <div class="types__top">
                <span class="types__name">{{ item.workTypeName }}</span>
                <span class="tag" :class="item.billingMode === 'BY_HOUR' ? 'tag--hour' : 'tag--count'">
                  {{ item.billingMode === 'BY_HOUR' ? '按小时' : '按次' }}
                </span>
                <span class="types__amount tabular">
                  ¥{{ formatMoneyShort(item.summary.totalAmount) }}
                </span>
              </div>
              <div class="types__bar">
                <div
                  class="types__fill"
                  :class="item.billingMode === 'BY_HOUR' ? 'types__fill--hour' : 'types__fill--count'"
                  :style="{
                    width: `${
                      report.summary.totalAmount > 0
                        ? Math.max(3, (item.summary.totalAmount / report.summary.totalAmount) * 100)
                        : 0
                    }%`,
                  }"
                />
              </div>
              <div class="types__meta tabular">
                {{ item.summary.recordCount }} 条
                <span class="dot-sep">/</span>
                正常 {{ formatHours(item.summary.normalHoursTotal) }}
                <span class="dot-sep">/</span>
                加班 {{ formatHours(item.summary.overtimeHoursTotal) }}
              </div>
            </li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

/* ---------- 页头 ---------- */
.dash__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.dash__head-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.dash__title {
  font-size: var(--text-2xl);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-tight);
  line-height: 1.15;
  margin-top: 2px;
}

.dash__sub {
  font-size: var(--text-xs);
  color: var(--color-fg-tertiary);
  margin-top: 2px;
}

.dash__head-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.dash__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  margin-left: 2px;
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.24);
  font-size: var(--text-2xs);
  font-weight: var(--weight-bold);
}

/* ---------- 指标区：主数字 + 细条 ---------- */
.metrics {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-xs);
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

/* ---------- 提醒 ---------- */
.alerts {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.alert-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  padding: 9px var(--space-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  text-align: left;
  color: inherit;
  transition: border-color var(--duration-fast) var(--ease-in-out);
}

.alert-row:hover {
  border-color: var(--color-border-strong);
}

.alert-row__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: 0 0 7px;
}

.alert-row--warning .alert-row__dot {
  background: var(--color-warning);
}
.alert-row--danger .alert-row__dot {
  background: var(--color-danger);
}

.alert-row__text {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex: 1 1 auto;
  min-width: 0;
  flex-wrap: wrap;
}

.alert-row__text strong {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--color-fg);
}

.alert-row__text span {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
}

.alert-row__arrow {
  color: var(--color-fg-tertiary);
  flex: 0 0 auto;
}

/* ---------- 主体双栏 ---------- */
.dash__body {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-lg);
  align-items: start;
}

@media (min-width: 1000px) {
  .dash__body {
    grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  }
}

.dash__col {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
  min-width: 0;
}

/* 面板：无边框，靠留白与细线分组 */
.panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.panel__more {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  border: 0;
  background: transparent;
  color: var(--color-accent);
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  padding: 2px 0;
  flex: 0 0 auto;
}

.panel__empty {
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
}

/* ---------- 待确认队列 ---------- */
.queue {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--color-divider);
}

.queue__row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  padding: 10px 2px;
  border: 0;
  border-bottom: 1px solid var(--color-divider);
  background: transparent;
  text-align: left;
  color: inherit;
  transition: background-color var(--duration-fast) var(--ease-in-out);
}

.queue__row:hover {
  background: var(--color-surface-muted);
}

.queue__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-sunken);
  color: var(--color-fg-secondary);
  font-size: var(--text-2xs);
  font-weight: var(--weight-bold);
}

.queue__body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  flex: 1 1 auto;
  min-width: 0;
}

.queue__title {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--color-fg);
}

.queue__type {
  font-size: var(--text-2xs);
  font-weight: var(--weight-normal);
  color: var(--color-fg-tertiary);
  padding: 0 4px;
  border-radius: var(--radius-xs);
  background: var(--color-surface-muted);
}

.queue__meta {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue__amount {
  font-size: var(--text-sm);
  font-weight: var(--weight-bold);
  flex: 0 0 auto;
  white-space: nowrap;
}

/* ---------- 动态流 ---------- */
.feed {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.feed__item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 7px 0;
  border-bottom: 1px dashed var(--color-divider);
  font-size: var(--text-xs);
  color: var(--color-fg-secondary);
}

.feed__item:last-child {
  border-bottom: 0;
}

.feed__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--color-border-strong);
  flex: 0 0 5px;
}

.feed__text {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed__text strong {
  font-weight: var(--weight-semibold);
  color: var(--color-fg);
}

.feed__time {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  flex: 0 0 auto;
}

.feed__amount {
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--color-fg);
  flex: 0 0 auto;
  min-width: 64px;
  text-align: right;
}

/* ---------- 排行 ---------- */
.rank {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rank__item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
}

.rank__no {
  font-family: var(--font-mono);
  font-size: var(--text-2xs);
  font-weight: var(--weight-medium);
  color: var(--color-fg-tertiary);
  padding-top: 2px;
  flex: 0 0 18px;
  font-variant-numeric: tabular-nums;
}

.rank__body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex: 1 1 auto;
  min-width: 0;
}

.rank__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
}

.rank__name {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--color-fg);
}

.rank__amount {
  font-size: var(--text-sm);
  font-weight: var(--weight-bold);
  color: var(--color-fg);
}

.rank__bar {
  display: block;
  height: 3px;
  border-radius: var(--radius-full);
  background: var(--color-surface-muted);
  overflow: hidden;
}

.rank__fill {
  display: block;
  height: 100%;
  border-radius: var(--radius-full);
  background: linear-gradient(90deg, var(--brand-500), var(--brand-700));
}

.rank__meta {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
}

/* ---------- 类型分布 ---------- */
.types {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.types__top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.types__name {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--color-fg);
}

.types__amount {
  margin-left: auto;
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
}

.types__bar {
  height: 4px;
  border-radius: var(--radius-full);
  background: var(--color-surface-muted);
  overflow: hidden;
}

.types__fill {
  height: 100%;
  border-radius: var(--radius-full);
  transition: width var(--duration-slow) var(--ease-out);
}

.types__fill--hour {
  background: linear-gradient(90deg, #8b5cf6, #6d28d9);
}

.types__fill--count {
  background: linear-gradient(90deg, var(--brand-400), var(--brand-600));
}

.types__meta {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  margin-top: 4px;
}
</style>
