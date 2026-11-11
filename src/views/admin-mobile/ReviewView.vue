<script setup lang="ts">
/**
 * 管理端移动 · 审核
 * 支持逐条确认 / 驳回（原因必填）/ 批量确认
 */
import { computed, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import MonthSwitcher from '@/components/MonthSwitcher.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppSheet from '@/components/AppSheet.vue'
import MapView, { type MapPoint } from '@/components/MapView.vue'
import { useAuth, useTheme } from '@/stores/app'
import { useToast } from '@/stores/toast'
import {
  BizError,
  approveRecord,
  approveRecords,
  computeRecordSafe,
  getEmployeeName,
  getWorkType,
  getWorkTypeName,
  listAllRecords,
  listEngineers,
  rejectRecord,
} from '@/data/service'
import type { Employee, RecordStatus, WorkRecord } from '@/core/types'
import { ROUNDING_LABELS } from '@/core/billing'
import {
  currentMonthKey,
  formatDateLabel,
  formatFullDateTime,
  formatMoney,
  formatMoneyShort,
  weekdayLabel,
} from '@/core/date'

const { dataVersion, bumpDataVersion } = useAuth()
const { theme, toggle } = useTheme()
const toast = useToast()

const monthKey = ref(currentMonthKey())
const statusFilter = ref<RecordStatus | 'ALL'>('PENDING')
const employeeFilter = ref('')
const empFilterOpen = ref(false)

/** 当前筛选的员工名 */
const selectedEmpName = computed(() => {
  if (!employeeFilter.value) return '全部员工'
  const emp = engineers.value.find((e) => e.id === employeeFilter.value)
  return emp ? emp.name : '全部员工'
})

/** 选择员工并关闭弹层 */
function pickEmployee(id: string): void {
  employeeFilter.value = id
  empFilterOpen.value = false
}

const selectedIds = ref<Set<string>>(new Set())

const engineers = ref<Employee[]>([])
const records = ref<WorkRecord[]>([])
const allRecords = ref<WorkRecord[]>([])
const loading = ref(false)

/** 加载审核列表（状态筛选在前端做） */
async function loadData(): Promise<void> {
  loading.value = true
  try {
    const [emps, all] = await Promise.all([
      listEngineers(),
      listAllRecords({
        monthKey: monthKey.value,
        employeeId: employeeFilter.value || undefined,
      }),
    ])
    engineers.value = emps
    allRecords.value = all
    applyFilter()
  } catch (err) {
    toast.error(err instanceof BizError ? err.message : '加载失败')
  } finally {
    loading.value = false
  }
}

/** 应用状态筛选 */
function applyFilter(): void {
  records.value =
    statusFilter.value === 'ALL'
      ? allRecords.value
      : allRecords.value.filter((r) => r.status === statusFilter.value)
}

watch(statusFilter, () => {
  applyFilter()
  selectedIds.value = new Set()
})
watch([monthKey, employeeFilter, dataVersion], () => {
  void loadData()
})

onMounted(() => {
  void loadData()
})

const counts = computed(() => ({
  ALL: allRecords.value.length,
  PENDING: allRecords.value.filter((r) => r.status === 'PENDING').length,
  APPROVED: allRecords.value.filter((r) => r.status === 'APPROVED').length,
  REJECTED: allRecords.value.filter((r) => r.status === 'REJECTED').length,
}))

const filters: Array<{ key: RecordStatus | 'ALL'; label: string }> = [
  { key: 'PENDING', label: '待确认' },
  { key: 'APPROVED', label: '已确认' },
  { key: 'REJECTED', label: '已驳回' },
  { key: 'ALL', label: '全部' },
]

const selectable = computed(() => records.value.filter((r) => r.status === 'PENDING'))
const selectedCount = computed(() => selectedIds.value.size)

const allSelected = computed(
  () => selectable.value.length > 0 && selectable.value.every((r) => selectedIds.value.has(r.id)),
)

function toggleSelect(id: string): void {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

function toggleSelectAll(): void {
  selectedIds.value = allSelected.value
    ? new Set()
    : new Set(selectable.value.map((r) => r.id))
}

/* ---------------- 详情 ---------------- */
const detailId = ref<string | null>(null)

const detailRecord = computed<WorkRecord | null>(() => {
  if (!detailId.value) return null
  return records.value.find((r) => r.id === detailId.value) ?? null
})

const detailPoints = computed<MapPoint[]>(() => {
  const r = detailRecord.value
  if (!r || !r.geoCaptured || r.latitude === null || r.longitude === null) return []
  return [{ id: r.id, lat: r.latitude, lng: r.longitude, label: getWorkTypeName(r.workTypeId), sublabel: r.location }]
})

const detailBilling = computed(() =>
  detailRecord.value ? computeRecordSafe(detailRecord.value) : null,
)

/* ---------------- 确认 ---------------- */
async function doApprove(record: WorkRecord): Promise<void> {
  try {
    await approveRecord(record.id)
    bumpDataVersion()
    toast.success(`已确认 ${getEmployeeName(record.employeeId)} 的记录`)
    detailId.value = null
  } catch (err) {
    toast.error(err instanceof BizError ? err.message : '确认失败')
  }
}

/* ---------------- 驳回 ---------------- */
const rejectOpen = ref(false)
const rejectTargetId = ref<string | null>(null)
const rejectReason = ref('')
const rejectError = ref('')

function openReject(id: string): void {
  rejectTargetId.value = id
  rejectReason.value = ''
  rejectError.value = ''
  rejectOpen.value = true
}

async function submitReject(): Promise<void> {
  if (!rejectTargetId.value) return
  rejectError.value = ''
  if (!rejectReason.value.trim()) {
    rejectError.value = '驳回时必须填写驳回原因'
    return
  }
  try {
    await rejectRecord(rejectTargetId.value, rejectReason.value)
    bumpDataVersion()
    toast.success('已驳回，员工可修改后重新提交')
    rejectOpen.value = false
    detailId.value = null
  } catch (err) {
    rejectError.value = err instanceof BizError ? err.message : '驳回失败'
  }
}

/* ---------------- 批量 ---------------- */
const bulkOpen = ref(false)

async function doBulkApprove(): Promise<void> {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  try {
    const result = await approveRecords(ids)
    bumpDataVersion()
    selectedIds.value = new Set()
    bulkOpen.value = false
    if (result.failed > 0) {
      toast.show(`已确认 ${result.success} 条，${result.failed} 条被跳过`)
    } else {
      toast.success(`已批量确认 ${result.success} 条记录`)
    }
  } catch (err) {
    toast.error(err instanceof BizError ? err.message : '批量确认失败')
  }
}
</script>

<template>
  <div class="m-review">
    <header class="m-head">
      <div class="m-head__row">
        <div class="grow">
          <p class="t-eyebrow">Review</p>
          <h1 class="m-head__title">记录审核</h1>
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
      <!-- 状态标签 -->
      <nav class="tabs" aria-label="按状态筛选">
        <button
          v-for="f in filters"
          :key="f.key"
          type="button"
          class="tabs__item"
          :class="{ 'tabs__item--active': statusFilter === f.key }"
          :aria-current="statusFilter === f.key ? 'true' : undefined"
          @click="statusFilter = f.key; selectedIds = new Set()"
        >
          {{ f.label }}
          <span class="tabs__count">{{ counts[f.key] }}</span>
        </button>
      </nav>

      <button type="button" class="filter-trigger" @click="empFilterOpen = true">
        <span class="filter-trigger__value">{{ selectedEmpName }}</span>
        <AppIcon name="chevron-down" :size="16" class="filter-trigger__arrow" />
      </button>

      <!-- 全选 -->
      <div v-if="selectable.length > 0" class="selectall">
        <button
          type="button"
          class="checkbox"
          role="checkbox"
          :aria-checked="allSelected"
          :aria-label="allSelected ? '取消全选' : '全选待确认记录'"
          @click="toggleSelectAll"
        >
          <AppIcon v-if="allSelected" name="check" :size="14" :stroke-width="3" />
        </button>
        <span class="text-sm text-secondary">全选 {{ selectable.length }} 条待确认</span>
      </div>

      <!-- 列表 -->
      <div v-if="records.length === 0" class="empty-box">
        <EmptyState
          icon="check-circle"
          :title="statusFilter === 'PENDING' ? '没有待确认记录' : '没有符合条件的记录'"
          :desc="statusFilter === 'PENDING' ? '所有记录都已处理完毕' : '试试切换状态或月份'"
        />
      </div>

      <div v-else class="rev-list">
        <article
          v-for="record in records"
          :key="record.id"
          class="rev-card"
          :class="{ 'rev-card--backfill': record.isBackfill }"
        >
          <div class="rev-card__head">
            <button
              v-if="record.status === 'PENDING'"
              type="button"
              class="checkbox"
              role="checkbox"
              :aria-checked="selectedIds.has(record.id)"
              :aria-label="`选择 ${getEmployeeName(record.employeeId)} 的记录`"
              @click="toggleSelect(record.id)"
            >
              <AppIcon v-if="selectedIds.has(record.id)" name="check" :size="14" :stroke-width="3" />
            </button>

            <span class="rev-card__avatar">{{ getEmployeeName(record.employeeId).slice(0, 1) }}</span>

            <div class="grow">
              <div class="rev-card__title">
                <span class="rev-card__name">{{ getEmployeeName(record.employeeId) }}</span>
                <span class="rev-card__type">{{ getWorkTypeName(record.workTypeId) }}</span>
                <StatusBadge :status="record.status" :is-backfill="record.isBackfill" />
              </div>
              <div class="rev-card__meta">
                {{ formatDateLabel(record.serviceDate) }}
                <span class="dot-sep">·</span>
                {{ record.startTime }}<template v-if="record.endTime">–{{ record.endTime }}</template>
                <span class="dot-sep">·</span>
                {{ record.location }}
              </div>
            </div>

            <span class="rev-card__amount tabular">
              ¥{{ formatMoneyShort(computeRecordSafe(record).total) }}
            </span>
          </div>

          <p class="rev-card__content">{{ record.content }}</p>

          <div v-if="record.status === 'REJECTED' && record.rejectReason" class="rev-card__reject">
            <AppIcon name="warning" :size="12" />
            <span>{{ record.rejectReason }}</span>
          </div>

          <div class="rev-card__foot">
            <button type="button" class="btn btn--ghost btn--sm" @click="detailId = record.id">
              <AppIcon name="eye" :size="14" />
              详情
            </button>

            <template v-if="record.status === 'PENDING'">
              <button type="button" class="btn btn--danger-ghost btn--sm" @click="openReject(record.id)">
                <AppIcon name="x" :size="14" />
                驳回
              </button>
              <button type="button" class="btn btn--primary btn--sm" @click="doApprove(record)">
                <AppIcon name="check" :size="14" :stroke-width="2.6" />
                确认
              </button>
            </template>
          </div>
        </article>
      </div>
    </div>

    <!-- 批量操作条 -->
    <Transition name="fade">
      <div v-if="selectedCount > 0" class="bulk-bar">
        <AppIcon name="check-circle" :size="17" />
        <span class="grow">已选择 {{ selectedCount }} 条</span>
        <button type="button" class="btn btn--ghost btn--sm bulk-cancel" @click="selectedIds = new Set()">
          取消
        </button>
        <button type="button" class="btn btn--sm bulk-confirm" @click="bulkOpen = true">
          批量确认
        </button>
      </div>
    </Transition>

    <!-- 详情 -->
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

          <div class="sheet-section">
            <span class="sheet-section__title">定位</span>
            <MapView :points="detailPoints" :height="160" :zoom="16" :interactive="false" />
          </div>

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
        </div>
      </template>

      <template #footer>
        <template v-if="detailRecord?.status === 'PENDING'">
          <button type="button" class="btn btn--danger-ghost" @click="openReject(detailRecord.id)">
            驳回
          </button>
          <button type="button" class="btn btn--primary" @click="doApprove(detailRecord)">
            确认记录
          </button>
        </template>
        <button v-else type="button" class="btn btn--secondary btn--block" @click="detailId = null">
          关闭
        </button>
      </template>
    </AppSheet>

    <!-- 驳回 -->
    <AppSheet :open="rejectOpen" title="驳回记录" @close="rejectOpen = false">
      <div class="col gap-md">
        <div class="alert alert--warning">
          <AppIcon name="info" :size="15" />
          <span>驳回后员工可修改并重新提交，将重走审核流程</span>
        </div>

        <div class="field">
          <label class="field__label" for="m-reject-reason">
            驳回原因
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <textarea
            id="m-reject-reason"
            v-model="rejectReason"
            class="textarea"
            rows="4"
            maxlength="200"
            placeholder="请说明驳回原因，员工可见"
            :aria-invalid="!!rejectError"
          />
          <div class="row row--between">
            <p v-if="rejectError" class="field__error">
              <AppIcon name="warning" :size="12" :stroke-width="2.4" />
              {{ rejectError }}
            </p>
            <span v-else class="field__hint">原因将展示给员工</span>
            <span class="text-xs text-tertiary tabular">{{ rejectReason.length }}/200</span>
          </div>
        </div>
      </div>

      <template #footer>
        <button type="button" class="btn btn--secondary" @click="rejectOpen = false">取消</button>
        <button type="button" class="btn btn--danger" @click="submitReject">确认驳回</button>
      </template>
    </AppSheet>

    <!-- 批量确认 -->
    <AppSheet :open="bulkOpen" title="批量确认记录" @close="bulkOpen = false">
      <p class="text-sm text-secondary">
        即将确认 <strong>{{ selectedCount }}</strong> 条记录。确认后记录锁定并计入本月提成核算。
      </p>
      <template #footer>
        <button type="button" class="btn btn--secondary" @click="bulkOpen = false">取消</button>
        <button type="button" class="btn btn--primary" @click="doBulkApprove">
          确认 {{ selectedCount }} 条
        </button>
      </template>
    </AppSheet>
    <!-- 员工筛选弹层 -->
    <AppSheet :open="empFilterOpen" title="按员工筛选" @close="empFilterOpen = false">
      <div class="filter-list">
        <button
          type="button"
          class="filter-option"
          :class="{ 'is-active': employeeFilter === '' }"
          @click="pickEmployee('')"
        >
          <span>全部员工</span>
          <AppIcon v-if="employeeFilter === ''" name="check" :size="18" />
        </button>
        <button
          v-for="emp in engineers"
          :key="emp.id"
          type="button"
          class="filter-option"
          :class="{ 'is-active': employeeFilter === emp.id }"
          @click="pickEmployee(emp.id)"
        >
          <span>{{ emp.name }}</span>
          <AppIcon v-if="employeeFilter === emp.id" name="check" :size="18" />
        </button>
      </div>
    </AppSheet>
  </div>
</template>

<style scoped>
.m-review {
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

.selectall {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 2px 0;
}

.empty-box {
  background: var(--color-surface);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
}

.rev-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.rev-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  transition: border-color var(--duration-fast) var(--ease-in-out);
}

.rev-card:hover {
  border-color: var(--color-border-strong);
  border-left-color: var(--color-accent);
}

.rev-card--backfill {
  border-left-color: var(--color-info);
}

.rev-card__head {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
}

.rev-card__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  border-radius: 50%;
  background: var(--color-accent-soft);
  color: var(--color-accent-soft-fg);
  font-size: var(--text-2xs);
  font-weight: var(--weight-bold);
}

.rev-card__title {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.rev-card__name {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
}

.rev-card__type {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  padding: 0 5px;
  border-radius: var(--radius-xs);
  background: var(--color-surface-muted);
}

.rev-card__meta {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  margin-top: 2px;
  font-variant-numeric: tabular-nums;
}

.rev-card__amount {
  font-size: var(--text-sm);
  font-weight: var(--weight-bold);
  flex: 0 0 auto;
  white-space: nowrap;
}

.rev-card__content {
  font-size: var(--text-xs);
  color: var(--color-fg-secondary);
  line-height: var(--leading-snug);
}

.rev-card__reject {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  padding: 5px 8px;
  border-radius: var(--radius-sm);
  background: var(--color-danger-bg);
  color: var(--color-danger);
  font-size: var(--text-2xs);
}

.rev-card__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-divider);
}

.bulk-cancel {
  color: rgba(255, 255, 255, 0.82);
}

.bulk-confirm {
  background: #fff;
  color: var(--color-accent);
  font-weight: 600;
}

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
