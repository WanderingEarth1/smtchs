<script setup lang="ts">
/**
 * 记录详情（员工端）—— 含计费明细、修正痕迹、编辑/删除
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppSheet from '@/components/AppSheet.vue'
import MapView, { type MapPoint } from '@/components/MapView.vue'
import { useAuth } from '@/stores/app'
import { useToast } from '@/stores/toast'
import {
  BizError,
  computeRecordSafe,
  deleteRecord,
  getRecord,
  getWorkType,
  listRevisions,
} from '@/data/service'
import { ROUNDING_LABELS } from '@/core/billing'
import {
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

const recordId = computed(() => route.params.id as string)

const record = computed(() => {
  void dataVersion.value
  return getRecord(recordId.value)
})

const workType = computed(() => (record.value ? getWorkType(record.value.workTypeId) : undefined))

const billing = computed(() => (record.value ? computeRecordSafe(record.value) : null))

const revisions = computed(() => {
  void dataVersion.value
  return listRevisions(recordId.value)
})

const canEdit = computed(
  () =>
    !!record.value &&
    record.value.employeeId === currentUser.value?.id &&
    record.value.status !== 'APPROVED',
)

const confirmDelete = ref(false)
const previewPhoto = ref<string | null>(null)

function goEdit(): void {
  if (!record.value) return
  void router.push(`/e/records/${record.value.id}/edit`)
}

function doDelete(): void {
  const user = currentUser.value
  if (!record.value || !user) return
  try {
    deleteRecord(record.value.id, user.id)
    bumpDataVersion()
    toast.success('记录已删除')
    confirmDelete.value = false
    void router.replace('/e/records')
  } catch (err) {
    toast.error(err instanceof BizError ? err.message : '删除失败')
  }
}

function back(): void {
  router.back()
}

function formatCoord(lat: number | null, lng: number | null): string {
  if (lat === null || lng === null) return '未获取定位'
  return `${lat.toFixed(5)}, ${lng.toFixed(5)}`
}

/** 地图点位 */
const mapPoints = computed<MapPoint[]>(() => {
  const r = record.value
  if (!r || !r.geoCaptured || r.latitude === null || r.longitude === null) return []
  return [
    {
      id: r.id,
      lat: r.latitude,
      lng: r.longitude,
      label: workType.value?.name ?? '服务地点',
      sublabel: r.location,
    },
  ]
})

/** 修正痕迹字段的中文标签 */
const FIELD_LABELS: Record<string, string> = {
  serviceDate: '服务日期',
  startTime: '开始时间',
  endTime: '结束时间',
  location: '服务地点',
  content: '工作内容',
}

function fieldLabel(key: string): string {
  return FIELD_LABELS[key] ?? key
}
</script>

<template>
  <div class="detail-view">
    <header class="app-header">
      <button class="app-header__back" type="button" aria-label="返回" @click="back">
        <AppIcon name="chevron-left" :size="22" />
      </button>
      <h1 class="app-header__title">记录详情</h1>
      <button
        v-if="canEdit"
        type="button"
        class="btn btn--ghost btn--sm"
        @click="goEdit"
      >
        <AppIcon name="edit" :size="15" />
        修改
      </button>
    </header>

    <div v-if="!record" class="page">
      <div class="card">
        <div class="empty-state">
          <AppIcon name="inbox" :size="40" :stroke-width="1.4" class="empty-state__icon" />
          <p class="empty-state__title">记录不存在或已被删除</p>
          <button type="button" class="btn btn--secondary btn--sm mt-sm" @click="router.replace('/e/records')">
            返回列表
          </button>
        </div>
      </div>
    </div>

    <div v-else class="page">
      <!-- 状态头 -->
      <section class="detail-head">
        <div class="detail-head__row">
          <div class="grow">
            <h2 class="detail-head__type">{{ workType?.name }}</h2>
            <p class="detail-head__serial mono">{{ record.serialNo }}</p>
          </div>
          <StatusBadge :status="record.status" :is-backfill="record.isBackfill" />
        </div>

        <div v-if="record.status === 'REJECTED' && record.rejectReason" class="alert alert--danger">
          <AppIcon name="warning" :size="16" />
          <div class="grow">
            <p class="fw-600">驳回原因</p>
            <p>{{ record.rejectReason }}</p>
            <p class="text-xs mt-sm">请修改后重新提交，将重走审核流程</p>
          </div>
        </div>

        <div v-if="record.status === 'PENDING'" class="alert alert--warning">
          <AppIcon name="clock" :size="16" />
          <span>等待管理员确认，确认前可自行修改或删除</span>
        </div>

        <div v-if="record.status === 'APPROVED'" class="alert alert--success">
          <AppIcon name="check-circle" :size="16" />
          <span>记录已确认并锁定，已计入提成核算</span>
        </div>
      </section>

      <!-- 服务信息 -->
      <section class="block">
        <div class="block__head">
          <span class="t-eyebrow">Details</span>
          <h3 class="block__title">服务信息</h3>
        </div>
        <div class="kv-list">
          <div class="kv-row">
            <span class="kv-row__k">服务日期</span>
            <span class="kv-row__v">
              {{ formatDateLabel(record.serviceDate) }} {{ weekdayLabel(record.serviceDate) }}
              <span class="text-tertiary text-xs">（{{ record.serviceDate }}）</span>
            </span>
          </div>
          <div class="kv-row">
            <span class="kv-row__k">{{ workType?.billingMode === 'BY_HOUR' ? '服务时段' : '上门时间' }}</span>
            <span class="detail-row__value tabular">
              {{ record.startTime }}<template v-if="record.endTime"> ~ {{ record.endTime }}</template>
              <span v-if="billing?.crossMidnight" class="tag tag--neutral" style="margin-left: 6px">
                跨 0 点
              </span>
            </span>
          </div>
          <div class="kv-row">
            <span class="kv-row__k">服务地点</span>
            <span class="kv-row__v">{{ record.location }}</span>
          </div>
          <div class="kv-row">
            <span class="kv-row__k">GPS 定位</span>
            <span class="kv-row__v">
              <template v-if="record.geoCaptured">
                <span class="mono text-sm">{{ formatCoord(record.latitude, record.longitude) }}</span>
              </template>
              <span v-else class="text-danger">未获取定位</span>
            </span>
          </div>
          <div class="kv-row">
            <span class="kv-row__k">工作内容</span>
            <span class="kv-row__v">{{ record.content }}</span>
          </div>
          <div v-if="record.remark" class="kv-row">
            <span class="kv-row__k">备注</span>
            <span class="kv-row__v">{{ record.remark }}</span>
          </div>
        </div>
      </section>

      <!-- 定位地图 -->
      <section class="block">
        <div class="block__head block__head--row">
          <div>
            <span class="t-eyebrow">Location</span>
            <h3 class="block__title">服务定位</h3>
          </div>
          <span v-if="record.geoCaptured" class="block__count mono">
            {{ record.latitude?.toFixed(4) }}, {{ record.longitude?.toFixed(4) }}
          </span>
        </div>
        <MapView :points="mapPoints" :height="180" :zoom="16" :interactive="false" />
      </section>

      <!-- 现场凭证 -->
      <section class="block">
        <div class="block__head block__head--row">
          <div>
            <span class="t-eyebrow">Evidence</span>
            <h3 class="block__title">现场凭证</h3>
          </div>
          <span class="block__count">{{ record.photos.length }} 张</span>
        </div>
        <div class="photo-list">
          <button
            v-for="(photo, i) in record.photos"
            :key="photo.id"
            type="button"
            class="photo-thumb"
            :aria-label="`查看第 ${i + 1} 张凭证`"
            @click="previewPhoto = photo.dataUrl"
          >
            <img :src="photo.dataUrl" :alt="`现场凭证 ${i + 1}`" loading="lazy" />
          </button>
        </div>
      </section>

      <!-- 计费明细 -->
      <section v-if="billing" class="block">
        <div class="block__head block__head--row">
          <div>
            <span class="t-eyebrow">Billing</span>
            <h3 class="block__title">计费明细</h3>
          </div>
          <span class="tag" :class="billing.billingMode === 'BY_HOUR' ? 'tag--hour' : 'tag--count'">
            {{ billing.billingMode === 'BY_HOUR' ? '按小时' : '按次' }}
          </span>
        </div>

        <div class="kv-list">
          <div class="kv-row">
            <span class="kv-row__k">单价</span>
            <span class="detail-row__value tabular">
              ¥{{ formatMoney(record.billingSnapshot.unitPrice) }}/{{
                billing.billingMode === 'BY_HOUR' ? '小时' : '次'
              }}
            </span>
          </div>
          <template v-if="billing.billingMode === 'BY_HOUR'">
            <div class="kv-row">
              <span class="kv-row__k">正常时长</span>
              <span class="detail-row__value tabular">
                {{ formatHours(billing.normalHours) }}
                <span
                  v-if="billing.normalHours !== billing.rawNormalHours"
                  class="text-tertiary text-xs"
                >
                  （原始 {{ formatHours(billing.rawNormalHours) }}，已取整）
                </span>
              </span>
            </div>
            <div class="kv-row">
              <span class="kv-row__k">加班时长</span>
              <span class="detail-row__value tabular">
                {{ formatHours(billing.overtimeHours) }}
                <span
                  v-if="billing.overtimeHours !== billing.rawOvertimeHours"
                  class="text-tertiary text-xs"
                >
                  （原始 {{ formatHours(billing.rawOvertimeHours) }}，已取整）
                </span>
              </span>
            </div>
            <div class="kv-row">
              <span class="kv-row__k">加班规则</span>
              <span class="kv-row__v">
                {{ record.billingSnapshot.overtimeStart }} 起算 · ×{{ record.billingSnapshot.overtimeMultiplier }} ·
                {{ ROUNDING_LABELS[record.billingSnapshot.roundingRule] }}
              </span>
            </div>
          </template>
        </div>

        <div class="billing-box mt-sm">
          <p>{{ billing.explanation }}</p>
          <div class="billing-box__total">
            <span>{{ record.status === 'APPROVED' ? '核算金额' : '预估金额' }}</span>
            <span class="billing-box__amount">¥{{ formatMoney(billing.total) }}</span>
          </div>
        </div>

        <p v-if="record.status !== 'APPROVED'" class="field__hint mt-sm">
          该记录尚未确认，金额为预估值，不计入当月提成
        </p>
      </section>

      <!-- 修正痕迹 -->
      <section v-if="revisions.length > 0" class="block">
        <div class="block__head block__head--row">
          <div>
            <span class="t-eyebrow">Revisions</span>
            <h3 class="block__title">修正痕迹</h3>
          </div>
          <span class="block__count">{{ revisions.length }} 次</span>
        </div>
        <div class="revision-list">
          <div v-for="rev in revisions" :key="rev.id" class="revision">
            <div class="revision__head">
              <span class="fw-600 text-sm">{{ rev.operatorName }} 修正</span>
              <span class="text-xs text-tertiary">{{ formatFullDateTime(rev.revisedAt) }}</span>
            </div>
            <p class="revision__note">{{ rev.note }}</p>
            <div class="revision__diff">
              <div v-for="(val, key) in rev.before" :key="key">
                <template v-if="rev.after[key] !== val">
                  <span class="text-xs text-tertiary">{{ fieldLabel(String(key)) }}：</span>
                  <span class="text-xs text-danger">{{ val ?? '—' }}</span>
                  <span class="text-xs text-tertiary"> → </span>
                  <span class="text-xs text-success">{{ rev.after[key] ?? '—' }}</span>
                </template>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 时间线 -->
      <section class="block">
        <div class="block__head">
          <span class="t-eyebrow">Timeline</span>
          <h3 class="block__title">流转记录</h3>
        </div>
        <div class="kv-list">
          <div class="kv-row">
            <span class="kv-row__k">提交时间</span>
            <span class="kv-row__v">{{ formatFullDateTime(record.submittedAt) }}</span>
          </div>
          <div v-if="record.confirmedAt" class="kv-row">
            <span class="kv-row__k">确认时间</span>
            <span class="kv-row__v">{{ formatFullDateTime(record.confirmedAt) }}</span>
          </div>
          <div v-if="record.rejectedAt" class="kv-row">
            <span class="kv-row__k">驳回时间</span>
            <span class="kv-row__v">{{ formatFullDateTime(record.rejectedAt) }}</span>
          </div>
        </div>
      </section>

      <!-- 操作 -->
      <div v-if="canEdit" class="detail-actions">
        <button type="button" class="btn btn--secondary grow" @click="goEdit">
          <AppIcon name="edit" :size="16" />
          修改记录
        </button>
        <button type="button" class="btn btn--danger-ghost" @click="confirmDelete = true">
          <AppIcon name="trash" :size="16" />
          删除
        </button>
      </div>
    </div>

    <!-- 删除确认 -->
    <AppSheet
      :open="confirmDelete"
      title="确认删除这条记录？"
      @close="confirmDelete = false"
    >
      <p class="text-sm text-secondary">
        删除后无法恢复。该记录当前状态为「{{ record?.status === 'REJECTED' ? '已驳回' : '待确认' }}」，
        删除后不再参与提成核算。
      </p>
      <template #footer>
        <button type="button" class="btn btn--secondary" @click="confirmDelete = false">取消</button>
        <button type="button" class="btn btn--danger" @click="doDelete">确认删除</button>
      </template>
    </AppSheet>

    <!-- 照片预览 -->
    <Teleport to="body">
      <div
        v-if="previewPhoto"
        class="photo-viewer"
        role="dialog"
        aria-modal="true"
        aria-label="照片预览"
        @click="previewPhoto = null"
      >
        <img :src="previewPhoto" alt="现场凭证大图" />
        <button type="button" class="photo-viewer__close" aria-label="关闭预览" @click="previewPhoto = null">
          <AppIcon name="x" :size="22" />
        </button>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* 区块：无边框，靠留白与细线分组 */
.block {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-md);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}

.block__head {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--color-divider);
}

.block__head--row {
  flex-direction: row;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-sm);
}

.block__title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-snug);
  color: var(--color-fg);
}

.block__count {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  flex: 0 0 auto;
  padding-top: 2px;
}

.detail-view {
  min-height: 100dvh;
  background: var(--color-bg);
}

@media (min-width: 640px) {
  .detail-view {
    max-width: var(--app-max-width);
    margin: 0 auto;
    border-left: 1px solid var(--color-border);
    border-right: 1px solid var(--color-border);
  }
}

.detail-head {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.detail-head__row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
}

.detail-head__type {
  font-size: var(--text-xl);
  font-weight: 700;
  letter-spacing: -0.01em;
}

.detail-head__serial {
  font-size: 11px;
  color: var(--color-fg-tertiary);
  margin-top: 2px;
}

.photo-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}

.photo-thumb {
  width: 96px;
  height: 96px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-surface-muted);
}

.photo-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.revision-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}

.revision {
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted);
  border-left: 3px solid var(--color-warning);
}

.revision__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.revision__note {
  font-size: var(--text-xs);
  color: var(--color-fg-secondary);
  margin-top: 2px;
}

.revision__diff {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 4px;
}

.detail-actions {
  display: flex;
  gap: var(--space-sm);
  padding-top: var(--space-sm);
}

.photo-viewer {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(2, 6, 23, 0.92);
  padding: var(--space-lg);
}

.photo-viewer img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: var(--radius-md);
}

.photo-viewer__close {
  position: absolute;
  top: calc(var(--safe-top) + 12px);
  right: 12px;
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}
</style>
