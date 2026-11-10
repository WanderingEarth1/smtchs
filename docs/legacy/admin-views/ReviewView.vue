<script setup lang="ts">
/**
 * 审核管理（AUD-01 ~ AUD-05）
 * - 待确认列表，突出补录标记
 * - 按员工 / 日期 / 工作类型筛选
 * - 逐条确认 / 驳回（驳回原因必填）
 * - 批量确认
 * - 修正已确认记录（留痕）
 */
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppSheet from '@/components/AppSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import MonthSwitcher from '@/components/MonthSwitcher.vue'
import { useAuth } from '@/stores/app'
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
  listWorkTypes,
  rejectRecord,
  reviseRecord,
} from '@/data/service'
import type { RecordStatus, WorkRecord } from '@/core/types'
import { ROUNDING_LABELS } from '@/core/billing'
import {
  currentMonthKey,
  formatDateLabel,
  formatFullDateTime,
  formatHours,
  formatMoney,
  weekdayLabel,
} from '@/core/date'

const route = useRoute()
const router = useRouter()
const { currentUser, dataVersion, bumpDataVersion } = useAuth()
const toast = useToast()

const monthKey = ref(currentMonthKey())
const statusFilter = ref<RecordStatus | 'ALL'>('PENDING')
const employeeFilter = ref('')
const workTypeFilter = ref('')
const keyword = ref('')

const selectedIds = ref<Set<string>>(new Set())

const engineers = computed(() => {
  void dataVersion.value
  return listEngineers()
})

const workTypes = computed(() => {
  void dataVersion.value
  return listWorkTypes()
})

const records = computed(() => {
  void dataVersion.value
  return listAllRecords({
    monthKey: monthKey.value,
    status: statusFilter.value,
    employeeId: employeeFilter.value || undefined,
    workTypeId: workTypeFilter.value || undefined,
    keyword: keyword.value,
  })
})

const counts = computed(() => {
  void dataVersion.value
  const all = listAllRecords({
    monthKey: monthKey.value,
    employeeId: employeeFilter.value || undefined,
    workTypeId: workTypeFilter.value || undefined,
    keyword: keyword.value,
  })
  return {
    ALL: all.length,
    PENDING: all.filter((r) => r.status === 'PENDING').length,
    APPROVED: all.filter((r) => r.status === 'APPROVED').length,
    REJECTED: all.filter((r) => r.status === 'REJECTED').length,
  }
})

const backfillCount = computed(() => records.value.filter((r) => r.isBackfill).length)

const selectableRecords = computed(() =>
  records.value.filter((r) => r.status === 'PENDING'),
)

const allSelected = computed(
  () =>
    selectableRecords.value.length > 0 &&
    selectableRecords.value.every((r) => selectedIds.value.has(r.id)),
)

const selectedCount = computed(() => selectedIds.value.size)

// 从 URL 读取初始状态筛选
watch(
  () => route.query.status,
  (v) => {
    if (typeof v === 'string' && ['PENDING', 'APPROVED', 'REJECTED', 'ALL'].includes(v)) {
      statusFilter.value = v as RecordStatus | 'ALL'
    }
  },
  { immediate: true },
)

// 切换筛选时清空选择
watch([statusFilter, employeeFilter, workTypeFilter, monthKey], () => {
  selectedIds.value = new Set()
})

/* ---------------- 详情抽屉 ---------------- */
const detailId = ref<string | null>(null)
const detailRecord = computed(() => {
  void dataVersion.value
  if (!detailId.value) return null
  return records.value.find((r) => r.id === detailId.value) ?? null
})

function openDetail(id: string): void {
  detailId.value = id
}

function closeDetail(): void {
  detailId.value = null
}

/* ---------------- 确认 / 驳回 ---------------- */
const rejectOpen = ref(false)
const rejectReason = ref('')
const rejectError = ref('')
const rejectTargetId = ref<string | null>(null)

function doApprove(record: WorkRecord): void {
  try {
    approveRecord(record.id)
    bumpDataVersion()
    toast.success(`已确认 ${getEmployeeName(record.employeeId)} 的记录`)
    closeDetail()
  } catch (err) {
    toast.error(err instanceof BizError ? err.message : '确认失败')
  }
}

function openReject(id: string): void {
  rejectTargetId.value = id
  rejectReason.value = ''
  rejectError.value = ''
  rejectOpen.value = true
}

function submitReject(): void {
  if (!rejectTargetId.value) return
  rejectError.value = ''
  if (!rejectReason.value.trim()) {
    rejectError.value = '驳回时必须填写驳回原因'
    return
  }
  try {
    rejectRecord(rejectTargetId.value, rejectReason.value)
    bumpDataVersion()
    toast.success('已驳回，员工可修改后重新提交')
    rejectOpen.value = false
    closeDetail()
  } catch (err) {
    rejectError.value = err instanceof BizError ? err.message : '驳回失败'
  }
}

/* ---------------- 批量确认 ---------------- */
const bulkConfirmOpen = ref(false)

function toggleSelect(id: string): void {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

function toggleSelectAll(): void {
  if (allSelected.value) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(selectableRecords.value.map((r) => r.id))
  }
}

function doBulkApprove(): void {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  const result = approveRecords(ids)
  bumpDataVersion()
  selectedIds.value = new Set()
  bulkConfirmOpen.value = false
  if (result.failed > 0) {
    toast.show(`已确认 ${result.success} 条，${result.failed} 条状态已变更被跳过`)
  } else {
    toast.success(`已批量确认 ${result.success} 条记录`)
  }
}

/* ---------------- 修正已确认记录（AUD-04） ---------------- */
const reviseOpen = ref(false)
const reviseTarget = ref<WorkRecord | null>(null)
const reviseForm = reactive({
  serviceDate: '',
  startTime: '',
  endTime: '',
  location: '',
  content: '',
  note: '',
})
const reviseError = ref('')

function openRevise(record: WorkRecord): void {
  reviseTarget.value = record
  reviseForm.serviceDate = record.serviceDate
  reviseForm.startTime = record.startTime
  reviseForm.endTime = record.endTime ?? ''
  reviseForm.location = record.location
  reviseForm.content = record.content
  reviseForm.note = ''
  reviseError.value = ''
  reviseOpen.value = true
}

function submitRevise(): void {
  const record = reviseTarget.value
  const user = currentUser.value
  if (!record || !user) return

  reviseError.value = ''
  try {
    reviseRecord(record.id, user.name, {
      serviceDate: reviseForm.serviceDate,
      startTime: reviseForm.startTime,
      endTime: reviseForm.endTime || null,
      location: reviseForm.location,
      content: reviseForm.content,
      note: reviseForm.note,
    })
    bumpDataVersion()
    toast.success('修正已保存，已记录修正痕迹')
    reviseOpen.value = false
    closeDetail()
  } catch (err) {
    reviseError.value = err instanceof BizError ? err.message : '修正失败'
  }
}

/* ---------------- 工具 ---------------- */
const statusTabs: Array<{ key: RecordStatus | 'ALL'; label: string }> = [
  { key: 'PENDING', label: '待确认' },
  { key: 'APPROVED', label: '已确认' },
  { key: 'REJECTED', label: '已驳回' },
  { key: 'ALL', label: '全部' },
]

function resetFilters(): void {
  employeeFilter.value = ''
  workTypeFilter.value = ''
  keyword.value = ''
}

const hasFilter = computed(
  () => !!employeeFilter.value || !!workTypeFilter.value || !!keyword.value,
)

function goReports(): void {
  void router.push('/a/pc/reports')
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-head__title">审核管理</h1>
        <p class="page-head__desc">
          确认后记录锁定并计入提成；驳回需填写原因，员工可修改后重新提交
        </p>
      </div>
      <div class="page-head__actions">
        <MonthSwitcher v-model="monthKey" />
      </div>
    </div>

    <!-- 状态标签 -->
    <nav class="tabs" aria-label="按状态筛选">
      <button
        v-for="tab in statusTabs"
        :key="tab.key"
        type="button"
        class="tabs__item"
        :class="{ 'tabs__item--active': statusFilter === tab.key }"
        :aria-current="statusFilter === tab.key ? 'true' : undefined"
        @click="statusFilter = tab.key"
      >
        {{ tab.label }}
        <span class="tabs__count tabular">{{ counts[tab.key] }}</span>
      </button>
    </nav>

    <!-- 筛选栏 -->
    <div class="filter-bar review-filters">
      <div class="filter-bar__search">
        <AppIcon name="search" :size="16" class="filter-bar__search-icon" />
        <input
          v-model="keyword"
          class="input"
          type="search"
          placeholder="搜索员工 / 地点 / 内容 / 流水号"
          aria-label="搜索记录"
        />
      </div>

      <select v-model="employeeFilter" class="select review-select" aria-label="按员工筛选">
        <option value="">全部员工</option>
        <option v-for="emp in engineers" :key="emp.id" :value="emp.id">
          {{ emp.name }}{{ emp.status === 'DISABLED' ? '（已停用）' : '' }}
        </option>
      </select>

      <select v-model="workTypeFilter" class="select review-select" aria-label="按工作类型筛选">
        <option value="">全部类型</option>
        <option v-for="wt in workTypes" :key="wt.id" :value="wt.id">{{ wt.name }}</option>
      </select>

      <button v-if="hasFilter" type="button" class="btn btn--ghost btn--sm" @click="resetFilters">
        <AppIcon name="x" :size="14" />
        清除
      </button>
    </div>

    <!-- 补录提示 -->
    <div v-if="backfillCount > 0" class="alert alert--info review-backfill">
      <AppIcon name="clock-clockwise" :size="16" />
      <span>当前列表中有 <strong>{{ backfillCount }}</strong> 条补录记录，请重点核对</span>
    </div>

    <!-- 列表 -->
    <div v-if="records.length === 0" class="card">
      <EmptyState
        icon="check-circle"
        :title="statusFilter === 'PENDING' ? '没有待确认记录' : '没有符合条件的记录'"
        :desc="
          statusFilter === 'PENDING'
            ? '所有记录都已处理完毕，可切换到其他状态查看'
            : '试试调整筛选条件或切换月份'
        "
      >
        <button
          v-if="hasFilter"
          type="button"
          class="btn btn--secondary btn--sm mt-sm"
          @click="resetFilters"
        >
          清除筛选
        </button>
      </EmptyState>
    </div>

    <div v-else class="review-list">
      <!-- 全选 -->
      <div v-if="selectableRecords.length > 0" class="review-selectall">
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
        <span class="text-sm text-secondary">
          全选本页 {{ selectableRecords.length }} 条待确认
        </span>
      </div>

      <article
        v-for="record in records"
        :key="record.id"
        class="review-card"
        :class="{ 'review-card--backfill': record.isBackfill }"
      >
        <div class="review-card__head">
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

          <span class="review-card__avatar">{{ getEmployeeName(record.employeeId).slice(0, 1) }}</span>

          <div class="grow">
            <div class="review-card__title-row">
              <span class="review-card__name">{{ getEmployeeName(record.employeeId) }}</span>
              <span class="review-card__type">{{ getWorkTypeName(record.workTypeId) }}</span>
              <StatusBadge :status="record.status" :is-backfill="record.isBackfill" />
            </div>
            <div class="review-card__meta">
              <span class="review-card__meta-item">
                <AppIcon name="calendar" :size="12" />
                {{ formatDateLabel(record.serviceDate) }} {{ weekdayLabel(record.serviceDate) }}
              </span>
              <span class="review-card__meta-item">
                <AppIcon name="clock" :size="12" />
                {{ record.startTime }}<template v-if="record.endTime">~{{ record.endTime }}</template>
              </span>
              <span class="review-card__meta-item">
                <AppIcon name="map-pin" :size="12" />
                {{ record.location }}
              </span>
              <span v-if="!record.geoCaptured" class="review-card__meta-item text-danger">
                <AppIcon name="warning" :size="12" />
                未获取定位
              </span>
            </div>
          </div>

          <div class="review-card__amount tabular">
            ¥{{ formatMoney(computeRecordSafe(record).total) }}
          </div>
        </div>

        <p class="review-card__content">{{ record.content }}</p>

        <div v-if="record.status === 'REJECTED' && record.rejectReason" class="review-card__reject">
          <AppIcon name="warning" :size="13" />
          <span>驳回原因：{{ record.rejectReason }}</span>
        </div>

        <div class="review-card__foot">
          <div class="review-card__thumbs">
            <img
              v-for="(photo, i) in record.photos.slice(0, 3)"
              :key="photo.id"
              :src="photo.dataUrl"
              :alt="`凭证 ${i + 1}`"
              loading="lazy"
              class="review-card__thumb"
            />
            <span class="text-xs text-tertiary">{{ record.photos.length }} 张凭证</span>
          </div>

          <div class="review-card__actions">
            <button type="button" class="btn btn--ghost btn--sm" @click="openDetail(record.id)">
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

            <button
              v-else-if="record.status === 'APPROVED'"
              type="button"
              class="btn btn--secondary btn--sm"
              @click="openRevise(record)"
            >
              <AppIcon name="edit" :size="14" />
              修正
            </button>
          </div>
        </div>
      </article>
    </div>

    <!-- 批量操作条 -->
    <Transition name="fade">
      <div v-if="selectedCount > 0" class="bulk-bar">
        <AppIcon name="check-circle" :size="18" />
        <span class="grow">已选择 {{ selectedCount }} 条</span>
        <button type="button" class="btn btn--ghost btn--sm bulk-cancel" @click="selectedIds = new Set()">
          取消
        </button>
        <button type="button" class="btn btn--sm bulk-confirm" @click="bulkConfirmOpen = true">
          批量确认
        </button>
      </div>
    </Transition>

    <!-- 详情抽屉 -->
    <AppSheet
      :open="!!detailRecord"
      :title="detailRecord ? `${getEmployeeName(detailRecord.employeeId)} 的记录` : '记录详情'"
      @close="closeDetail"
    >
      <template v-if="detailRecord">
        <div class="sheet-detail">
          <div class="row row--between">
            <StatusBadge :status="detailRecord.status" :is-backfill="detailRecord.isBackfill" />
            <span class="mono text-xs text-tertiary">{{ detailRecord.serialNo }}</span>
          </div>

          <div class="detail-list">
            <div class="detail-row">
              <span class="detail-row__label">工作类型</span>
              <span class="detail-row__value">
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
            <div class="detail-row">
              <span class="detail-row__label">服务日期</span>
              <span class="detail-row__value">
                {{ formatDateLabel(detailRecord.serviceDate) }}
                {{ weekdayLabel(detailRecord.serviceDate) }}
              </span>
            </div>
            <div class="detail-row">
              <span class="detail-row__label">时间</span>
              <span class="detail-row__value tabular">
                {{ detailRecord.startTime
                }}<template v-if="detailRecord.endTime"> ~ {{ detailRecord.endTime }}</template>
              </span>
            </div>
            <div class="detail-row">
              <span class="detail-row__label">地点</span>
              <span class="detail-row__value">{{ detailRecord.location }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-row__label">定位</span>
              <span class="detail-row__value">
                <span v-if="detailRecord.geoCaptured" class="mono text-sm">
                  {{ detailRecord.latitude?.toFixed(5) }}, {{ detailRecord.longitude?.toFixed(5) }}
                </span>
                <span v-else class="text-danger">未获取定位</span>
              </span>
            </div>
            <div class="detail-row">
              <span class="detail-row__label">工作内容</span>
              <span class="detail-row__value">{{ detailRecord.content }}</span>
            </div>
            <div v-if="detailRecord.remark" class="detail-row">
              <span class="detail-row__label">备注</span>
              <span class="detail-row__value">{{ detailRecord.remark }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-row__label">提交时间</span>
              <span class="detail-row__value">{{ formatFullDateTime(detailRecord.submittedAt) }}</span>
            </div>
          </div>

          <!-- 凭证 -->
          <div class="sheet-photos">
            <img
              v-for="(photo, i) in detailRecord.photos"
              :key="photo.id"
              :src="photo.dataUrl"
              :alt="`凭证 ${i + 1}`"
              class="sheet-photo"
            />
          </div>

          <!-- 计费 -->
          <div class="billing-box">
            <p>{{ computeRecordSafe(detailRecord).explanation }}</p>
            <div class="billing-box__total">
              <span>核算金额</span>
              <span class="billing-box__amount">
                ¥{{ formatMoney(computeRecordSafe(detailRecord).total) }}
              </span>
            </div>
            <p class="text-xs text-tertiary mt-sm">
              单价 ¥{{ formatMoney(detailRecord.billingSnapshot.unitPrice) }} · 加班起算
              {{ detailRecord.billingSnapshot.overtimeStart }} · ×{{
                detailRecord.billingSnapshot.overtimeMultiplier
              }}
              · {{ ROUNDING_LABELS[detailRecord.billingSnapshot.roundingRule] }}
            </p>
          </div>

          <div v-if="detailRecord.status === 'REJECTED' && detailRecord.rejectReason" class="alert alert--danger">
            <AppIcon name="warning" :size="16" />
            <span>驳回原因：{{ detailRecord.rejectReason }}</span>
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
        <template v-else-if="detailRecord?.status === 'APPROVED'">
          <button type="button" class="btn btn--secondary" @click="closeDetail">关闭</button>
          <button type="button" class="btn btn--primary" @click="openRevise(detailRecord)">
            修正记录
          </button>
        </template>
        <template v-else>
          <button type="button" class="btn btn--secondary btn--block" @click="closeDetail">
            关闭
          </button>
        </template>
      </template>
    </AppSheet>

    <!-- 驳回弹层 -->
    <AppSheet :open="rejectOpen" title="驳回记录" @close="rejectOpen = false">
      <div class="col gap-md">
        <div class="alert alert--warning">
          <AppIcon name="info" :size="16" />
          <span>驳回后员工可修改并重新提交，将重走审核流程</span>
        </div>

        <div class="field">
          <label class="field__label" for="reject-reason">
            驳回原因
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <textarea
            id="reject-reason"
            v-model="rejectReason"
            class="textarea"
            rows="4"
            maxlength="200"
            placeholder="请说明驳回原因，员工可见，如：照片不清晰，无法确认安装完成情况"
            :aria-invalid="!!rejectError"
          />
          <div class="row row--between">
            <p v-if="rejectError" class="field__error">
              <AppIcon name="warning" :size="12" :stroke-width="2.4" />
              {{ rejectError }}
            </p>
            <span v-else class="field__hint">原因将展示给员工，请写清楚需要修改什么</span>
            <span class="text-xs text-tertiary tabular">{{ rejectReason.length }}/200</span>
          </div>
        </div>
      </div>

      <template #footer>
        <button type="button" class="btn btn--secondary" @click="rejectOpen = false">取消</button>
        <button type="button" class="btn btn--danger" @click="submitReject">确认驳回</button>
      </template>
    </AppSheet>

    <!-- 批量确认弹层 -->
    <AppSheet :open="bulkConfirmOpen" title="批量确认记录" @close="bulkConfirmOpen = false">
      <p class="text-sm text-secondary">
        即将确认 <strong>{{ selectedCount }}</strong> 条记录。确认后记录将锁定并计入本月提成核算，
        员工不可再自行修改。
      </p>
      <template #footer>
        <button type="button" class="btn btn--secondary" @click="bulkConfirmOpen = false">取消</button>
        <button type="button" class="btn btn--primary" @click="doBulkApprove">
          确认 {{ selectedCount }} 条
        </button>
      </template>
    </AppSheet>

    <!-- 修正弹层（AUD-04） -->
    <AppSheet :open="reviseOpen" title="修正已确认记录" @close="reviseOpen = false">
      <div class="col gap-md">
        <div class="alert alert--warning">
          <AppIcon name="shield" :size="16" />
          <span>修正将保留「修正人、修正时间、修正前原值」痕迹，记录按新值参与核算</span>
        </div>

        <div v-if="reviseError" class="alert alert--danger">
          <AppIcon name="warning" :size="16" />
          <span>{{ reviseError }}</span>
        </div>

        <div class="field">
          <label class="field__label" for="rev-date">服务日期</label>
          <input id="rev-date" v-model="reviseForm.serviceDate" class="input" type="date" />
        </div>

        <div class="row gap-sm">
          <div class="field grow">
            <label class="field__label" for="rev-start">开始时间</label>
            <input id="rev-start" v-model="reviseForm.startTime" class="input tabular" type="time" />
          </div>
          <div class="field grow">
            <label class="field__label" for="rev-end">结束时间</label>
            <input id="rev-end" v-model="reviseForm.endTime" class="input tabular" type="time" />
          </div>
        </div>

        <div class="field">
          <label class="field__label" for="rev-location">地点</label>
          <input id="rev-location" v-model="reviseForm.location" class="input" type="text" maxlength="60" />
        </div>

        <div class="field">
          <label class="field__label" for="rev-content">工作内容</label>
          <textarea id="rev-content" v-model="reviseForm.content" class="textarea" rows="2" maxlength="200" />
        </div>

        <div class="field">
          <label class="field__label" for="rev-note">
            修正说明
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <textarea
            id="rev-note"
            v-model="reviseForm.note"
            class="textarea"
            rows="2"
            maxlength="200"
            placeholder="如：员工反馈实际结束时间为 19:00，经电话核实后修正"
          />
        </div>
      </div>

      <template #footer>
        <button type="button" class="btn btn--secondary" @click="reviseOpen = false">取消</button>
        <button type="button" class="btn btn--primary" @click="submitRevise">保存修正</button>
      </template>
    </AppSheet>
  </div>
</template>

<style scoped>
/* 下划线式状态标签 */
.tabs {
  display: flex;
  gap: var(--space-lg);
  margin-bottom: var(--space-md);
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

.review-filters {
  margin-bottom: var(--space-md);
}

.review-select {
  flex: 0 1 160px;
  min-width: 120px;
}

.review-backfill {
  margin-bottom: var(--space-md);
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.review-selectall {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 6px 4px;
}

.review-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  transition:
    border-color var(--duration-fast) var(--ease-in-out),
    box-shadow var(--duration-fast) var(--ease-in-out);
}

.review-card:hover {
  border-color: var(--color-border-strong);
  border-left-color: var(--color-accent);
  box-shadow: var(--shadow-xs);
}

/* 补录：左侧竖条改为信息蓝，与普通记录区分 */
.review-card--backfill {
  border-left-color: var(--color-info);
}

.review-card__head {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
}

.review-card__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 50%;
  background: var(--color-primary);
  color: var(--color-primary-fg);
  font-size: 13px;
  font-weight: 700;
}

.review-card__title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.review-card__name {
  font-size: var(--text-sm);
  font-weight: 600;
}

.review-card__type {
  font-size: var(--text-xs);
  color: var(--color-fg-secondary);
  padding: 1px 6px;
  border-radius: var(--radius-xs);
  background: var(--color-surface-muted);
}

.review-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: 3px;
  font-size: 11px;
  color: var(--color-fg-tertiary);
}

.review-card__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.review-card__amount {
  font-size: var(--text-md);
  font-weight: 700;
  flex: 0 0 auto;
  white-space: nowrap;
}

.review-card__content {
  font-size: var(--text-sm);
  color: var(--color-fg-secondary);
  line-height: var(--leading-normal);
}

.review-card__reject {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  background: var(--color-danger-bg);
  color: var(--color-danger);
  font-size: var(--text-xs);
}

.review-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  flex-wrap: wrap;
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-divider);
}

.review-card__thumbs {
  display: flex;
  align-items: center;
  gap: 4px;
}

.review-card__thumb {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-xs);
  object-fit: cover;
  border: 1px solid var(--color-border);
}

.review-card__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.bulk-cancel {
  color: rgba(255, 255, 255, 0.82);
}

.bulk-cancel:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.bulk-confirm {
  background: #fff;
  color: var(--color-primary);
  font-weight: 600;
}

.bulk-confirm:hover {
  background: rgba(255, 255, 255, 0.9);
}

.sheet-detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.sheet-photos {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.sheet-photo {
  width: 88px;
  height: 88px;
  object-fit: cover;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
}
</style>
