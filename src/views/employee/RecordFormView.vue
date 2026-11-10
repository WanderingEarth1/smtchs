<script setup lang="ts">
/**
 * 新增 / 修改服务记录（REC-01 / REC-02 / REC-03）
 *
 * 交互设计要点：
 * - 计费模式决定时间字段（按次=上门时间；按小时=开始+结束）
 * - 非当日日期自动提示「补录」
 * - 定位自动采集，失败不阻断提交
 * - 实时金额预估，让工程师自查，减少月底争议（REC-05 的设计延伸）
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import PhotoUploader from '@/components/PhotoUploader.vue'
import MapView, { type MapPoint } from '@/components/MapView.vue'
import { useAuth, useTheme } from '@/stores/app'
import { useToast } from '@/stores/toast'
import {
  BizError,
  createRecord,
  getUnitPrice,
  getWorkType,
  listWorkTypes,
  getRecord,
  updateRecord,
  validateDraft,
  getConfig,
  computeRecordSafe,
  type RecordDraft,
} from '@/data/service'
import { calculateBilling, ROUNDING_LABELS } from '@/core/billing'
import type { PhotoRef, WorkRecord } from '@/core/types'
import { captureGeo, type GeoResult } from '@/core/photo'
import { formatDateLabel, formatMoney, formatMonthLabel, monthOf, todayKey, weekdayLabel } from '@/core/date'

const route = useRoute()
const router = useRouter()
const { currentUser, dataVersion, bumpDataVersion } = useAuth()
const { theme, toggle } = useTheme()
const toast = useToast()

const editingId = computed(() => route.params.id as string | undefined)
const isEditing = computed(() => !!editingId.value)

const workTypes = computed(() => {
  void dataVersion.value
  return listWorkTypes(false)
})

const config = computed(() => {
  void dataVersion.value
  return getConfig()
})

const draft = reactive<RecordDraft>({
  workTypeId: '',
  serviceDate: todayKey(),
  startTime: '09:00',
  endTime: '18:00',
  location: '',
  latitude: null,
  longitude: null,
  geoCaptured: false,
  content: '',
  remark: '',
  photos: [],
})

const errors = reactive<Record<string, string>>({})
const submitting = ref(false)
const geoState = ref<'idle' | 'loading' | 'ok' | 'failed'>('idle')
const geoMessage = ref('正在获取定位…')

const selectedType = computed(() => (draft.workTypeId ? getWorkType(draft.workTypeId) : undefined))
const isHourly = computed(() => selectedType.value?.billingMode === 'BY_HOUR')

const unitPrice = computed(() => {
  const user = currentUser.value
  if (!user || !draft.workTypeId) return 0
  void dataVersion.value
  return getUnitPrice(user.id, draft.workTypeId)
})

const isBackfill = computed(() => draft.serviceDate !== todayKey())

/** 实时金额预估 */
const preview = computed(() => {
  if (!draft.workTypeId || unitPrice.value <= 0) return null
  if (isHourly.value && (!draft.startTime || !draft.endTime)) return null
  if (!draft.startTime) return null
  try {
    return calculateBilling({
      billingMode: selectedType.value!.billingMode,
      unitPrice: unitPrice.value,
      startTime: draft.startTime,
      endTime: draft.endTime,
      overtimeStart: config.value.overtimeStart,
      overtimeMultiplier: config.value.overtimeMultiplier,
      roundingRule: config.value.roundingRule,
    })
  } catch {
    return null
  }
})

/* ---------------- 初始化（编辑模式回填） ---------------- */
onMounted(async () => {
  if (isEditing.value) {
    const record = getRecord(editingId.value!)
    const user = currentUser.value
    if (!record) {
      toast.error('记录不存在')
      void router.replace('/e/records')
      return
    }
    if (record.employeeId !== user?.id) {
      toast.error('只能修改自己的记录')
      void router.replace('/e/records')
      return
    }
    if (record.status === 'APPROVED') {
      toast.error('已确认的记录已锁定，无法修改')
      void router.replace(`/e/records/${record.id}`)
      return
    }
    fillFromRecord(record)
  } else {
    // 默认选第一个启用类型
    const first = workTypes.value[0]
    if (first) draft.workTypeId = first.id
    void refreshGeo()
  }
})

function fillFromRecord(record: WorkRecord): void {
  draft.workTypeId = record.workTypeId
  draft.serviceDate = record.serviceDate
  draft.startTime = record.startTime
  draft.endTime = record.endTime ?? '18:00'
  draft.location = record.location
  draft.latitude = record.latitude
  draft.longitude = record.longitude
  draft.geoCaptured = record.geoCaptured
  draft.content = record.content
  draft.remark = record.remark
  draft.photos = [...record.photos]
  geoState.value = record.geoCaptured ? 'ok' : 'failed'
  geoMessage.value = record.geoCaptured ? '已保留原定位' : '未获取定位'
}

/** 当前定位点（用于地图预览） */
const geoPoints = computed<MapPoint[]>(() => {
  if (!draft.geoCaptured || draft.latitude === null || draft.longitude === null) return []
  return [
    {
      id: 'current',
      lat: draft.latitude,
      lng: draft.longitude,
      label: '当前位置',
      sublabel: draft.location || '待填写地点',
    },
  ]
})

/* ---------------- 定位 ---------------- */
async function refreshGeo(): Promise<void> {
  geoState.value = 'loading'
  geoMessage.value = '正在获取定位…'
  const result: GeoResult = await captureGeo()
  if (result.ok) {
    draft.latitude = result.latitude
    draft.longitude = result.longitude
    draft.geoCaptured = true
    geoState.value = 'ok'
    geoMessage.value = `定位成功（${result.latitude!.toFixed(5)}, ${result.longitude!.toFixed(5)}）`
  } else {
    draft.latitude = null
    draft.longitude = null
    draft.geoCaptured = false
    geoState.value = 'failed'
    geoMessage.value = result.message
  }
}

/* ---------------- 表单交互 ---------------- */
function selectType(id: string): void {
  draft.workTypeId = id
  errors.workTypeId = ''
}

// 切换到按次类型时清空结束时间校验错误
watch(isHourly, (hourly) => {
  if (hourly && !draft.endTime) draft.endTime = '18:00'
  errors.endTime = ''
})

function setPhotos(photos: PhotoRef[]): void {
  draft.photos = photos
  if (photos.length > 0) errors.photos = ''
}

/** 快捷时间选择 */
function setTimePreset(kind: 'now' | 'morning' | 'afternoon' | 'evening'): void {
  const now = new Date()
  const hhmm = (h: number, m = 0) =>
    `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
  if (kind === 'now') {
    const h = now.getHours()
    const m = now.getMinutes()
    draft.startTime = hhmm(h, m)
    if (isHourly.value) draft.endTime = hhmm(Math.min(23, h + 2), m)
  } else if (kind === 'morning') {
    draft.startTime = '09:00'
    if (isHourly.value) draft.endTime = '12:00'
  } else if (kind === 'afternoon') {
    draft.startTime = '14:00'
    if (isHourly.value) draft.endTime = '18:00'
  } else {
    draft.startTime = '18:30'
    if (isHourly.value) draft.endTime = '21:00'
  }
}

/* ---------------- 提交 ---------------- */
async function submit(): Promise<void> {
  if (submitting.value) return
  const user = currentUser.value
  if (!user) return

  Object.keys(errors).forEach((k) => delete errors[k])
  const result = validateDraft(draft)
  Object.assign(errors, result)

  if (Object.keys(result).length > 0) {
    toast.error(Object.values(result)[0])
    // 滚动到第一个错误
    const firstKey = Object.keys(result)[0]
    document
      .querySelector(`[data-field="${firstKey}"]`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    return
  }

  submitting.value = true
  try {
    // 未定位时尝试最后一次采集（不阻断）
    if (!draft.geoCaptured && geoState.value !== 'failed') {
      await refreshGeo()
    }

    if (isEditing.value) {
      updateRecord(editingId.value!, user.id, draft)
      toast.success('记录已修改并重新提交审核')
      bumpDataVersion()
      await router.replace(`/e/records/${editingId.value}`)
    } else {
      const record = createRecord(user.id, draft)
      toast.success('记录提交成功，等待管理员确认')
      bumpDataVersion()
      await router.replace(`/e/records/${record.id}`)
    }
  } catch (err) {
    toast.error(err instanceof BizError ? err.message : '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function cancel(): void {
  router.back()
}
</script>

<template>
  <div class="form-view">
    <header class="app-header">
      <button class="app-header__back" type="button" aria-label="返回" @click="cancel">
        <AppIcon name="chevron-left" :size="22" />
      </button>
      <h1 class="app-header__title">{{ isEditing ? '修改记录' : '新增服务记录' }}</h1>
      <button
        type="button"
        class="btn btn--ghost btn--icon"
        :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
        @click="toggle"
      >
        <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" :size="18" />
      </button>
    </header>

    <form class="form-body" novalidate @submit.prevent="submit">
      <!-- 基础信息 -->
      <section class="form-section">
        <div class="form-section__head">
          <span class="t-eyebrow">Service Info</span>
          <h2 class="form-section__title">服务信息</h2>
        </div>

        <!-- 工作类型 -->
        <div class="field" data-field="workTypeId">
          <span class="field__label">
            工作类型
            <span class="field__required" aria-hidden="true">*</span>
            <span v-if="selectedType" class="tag" :class="isHourly ? 'tag--hour' : 'tag--count'">
              {{ isHourly ? '按小时' : '按次' }}
            </span>
          </span>
          <div class="type-grid" role="radiogroup" aria-label="选择工作类型">
            <button
              v-for="wt in workTypes"
              :key="wt.id"
              type="button"
              role="radio"
              class="type-chip"
              :aria-checked="draft.workTypeId === wt.id"
              :class="{ 'type-chip--active': draft.workTypeId === wt.id }"
              @click="selectType(wt.id)"
            >
              <span class="type-chip__name">{{ wt.name }}</span>
              <span class="type-chip__mode">{{ wt.billingMode === 'BY_HOUR' ? '按小时' : '按次' }}</span>
            </button>
          </div>
          <p v-if="errors.workTypeId" class="field__error">
            <AppIcon name="warning" :size="12" :stroke-width="2.4" />
            {{ errors.workTypeId }}
          </p>
          <p v-else-if="unitPrice > 0" class="field__hint">
            当前单价 ¥{{ formatMoney(unitPrice) }}/{{ isHourly ? '小时' : '次' }}（由管理员配置）
          </p>
          <p v-else class="field__error">
            <AppIcon name="warning" :size="12" :stroke-width="2.4" />
            该类型尚未配置单价，请先联系管理员
          </p>
        </div>

        <!-- 日期 -->
        <div class="field" data-field="serviceDate">
          <label class="field__label" for="f-date">
            服务日期
            <span class="field__required" aria-hidden="true">*</span>
            <span v-if="isBackfill" class="flag-label flag-label--backfill">补录</span>
          </label>
          <input
            id="f-date"
            v-model="draft.serviceDate"
            class="input"
            type="date"
            :max="todayKey()"
            :aria-invalid="!!errors.serviceDate"
            @input="errors.serviceDate = ''"
          />
          <p v-if="errors.serviceDate" class="field__error">
            <AppIcon name="warning" :size="12" :stroke-width="2.4" />
            {{ errors.serviceDate }}
          </p>
          <p v-else-if="isBackfill" class="field__hint">
            非当日记录将自动标记「补录」，管理员审核时会重点核对
          </p>
          <p v-else class="field__hint">
            {{ formatDateLabel(draft.serviceDate) }} {{ weekdayLabel(draft.serviceDate) }}
          </p>
        </div>

        <!-- 时间 -->
        <div class="field" data-field="startTime">
          <span class="field__label">
            {{ isHourly ? '服务时段' : '上门时间' }}
            <span class="field__required" aria-hidden="true">*</span>
          </span>

          <div class="time-presets" role="group" aria-label="快捷时间">
            <button type="button" class="time-preset" @click="setTimePreset('now')">此刻</button>
            <button type="button" class="time-preset" @click="setTimePreset('morning')">上午</button>
            <button type="button" class="time-preset" @click="setTimePreset('afternoon')">下午</button>
            <button type="button" class="time-preset" @click="setTimePreset('evening')">晚间</button>
          </div>

          <div class="time-row" :class="{ 'time-row--single': !isHourly }">
            <div class="time-input">
              <label class="time-input__label" for="f-start">开始</label>
              <input
                id="f-start"
                v-model="draft.startTime"
                class="input tabular"
                type="time"
                :aria-invalid="!!errors.startTime"
                @input="errors.startTime = ''"
              />
            </div>
            <template v-if="isHourly">
              <span class="time-sep" aria-hidden="true">→</span>
              <div class="time-input">
                <label class="time-input__label" for="f-end">结束</label>
                <input
                  id="f-end"
                  v-model="draft.endTime as string"
                  class="input tabular"
                  type="time"
                  :aria-invalid="!!errors.endTime"
                  @input="errors.endTime = ''"
                />
              </div>
            </template>
          </div>

          <p v-if="errors.startTime || errors.endTime" class="field__error">
            <AppIcon name="warning" :size="12" :stroke-width="2.4" />
            {{ errors.startTime || errors.endTime }}
          </p>
          <p v-else-if="isHourly && draft.endTime && draft.startTime && draft.endTime < draft.startTime" class="field__hint">
            结束时间早于开始时间，将按跨 0 点连续服务计费（加班段）
          </p>
        </div>

        <!-- 地点 -->
        <div class="field" data-field="location">
          <label class="field__label" for="f-location">
            服务地点
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <input
            id="f-location"
            v-model="draft.location"
            class="input"
            type="text"
            maxlength="60"
            placeholder="如：滨江花园 3 栋 2 单元 502"
            autocomplete="off"
            :aria-invalid="!!errors.location"
            @input="errors.location = ''"
          />
          <p v-if="errors.location" class="field__error">
            <AppIcon name="warning" :size="12" :stroke-width="2.4" />
            {{ errors.location }}
          </p>
        </div>

        <!-- GPS 定位 -->
        <div class="field">
          <span class="field__label">GPS 定位</span>
          <div class="geo-box" :class="`geo-box--${geoState}`">
            <AppIcon
              :name="geoState === 'ok' ? 'crosshair' : geoState === 'loading' ? 'spinner' : 'map-pin'"
              :size="16"
              :class="{ spin: geoState === 'loading' }"
            />
            <span class="grow geo-box__text">{{ geoMessage }}</span>
            <button
              type="button"
              class="btn btn--ghost btn--sm"
              :disabled="geoState === 'loading'"
              @click="refreshGeo"
            >
              重新获取
            </button>
          </div>
          <MapView
            v-if="geoPoints.length > 0"
            :points="geoPoints"
            :height="150"
            :zoom="16"
            :interactive="false"
          />
          <p class="field__hint">定位失败不影响提交，记录会标记「未获取定位」</p>
        </div>

        <!-- 工作内容 -->
        <div class="field" data-field="content">
          <label class="field__label" for="f-content">
            工作内容
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <textarea
            id="f-content"
            v-model="draft.content"
            class="textarea"
            rows="3"
            maxlength="200"
            placeholder="描述本次上门做了什么，如：热水器不加热，更换加热管并测试"
            :aria-invalid="!!errors.content"
            @input="errors.content = ''"
          />
          <div class="field__foot">
            <p v-if="errors.content" class="field__error">
              <AppIcon name="warning" :size="12" :stroke-width="2.4" />
              {{ errors.content }}
            </p>
            <span v-else class="field__hint">简要说明服务内容，便于审核核对</span>
            <span class="field__counter tabular">{{ draft.content.length }}/200</span>
          </div>
        </div>

        <!-- 照片 -->
        <div class="field" data-field="photos">
          <span class="field__label">
            现场凭证
            <span class="field__required" aria-hidden="true">*</span>
          </span>
          <PhotoUploader
            :model-value="draft.photos"
            :error="errors.photos"
            @update:model-value="setPhotos"
          />
        </div>

        <!-- 备注 -->
        <div class="field" data-field="remark">
          <label class="field__label" for="f-remark">备注（选填）</label>
          <textarea
            id="f-remark"
            v-model="draft.remark"
            class="textarea"
            rows="2"
            maxlength="200"
            placeholder="补充说明，如客户特殊要求、遗留问题等"
          />
        </div>
      </section>

      <!-- 实时计费预估 -->
      <section v-if="preview" class="form-section preview" aria-live="polite">
        <div class="form-section__head form-section__head--row">
          <div>
            <span class="t-eyebrow">Estimate</span>
            <h2 class="form-section__title">金额预估</h2>
          </div>
          <span class="preview__tag">实时</span>
        </div>
        <p class="preview__explain">{{ preview.explanation }}</p>
        <div class="preview__total">
          <span class="preview__total-label">本次预计</span>
          <span class="preview__total-amount tabular">¥{{ formatMoney(preview.total) }}</span>
        </div>
        <p class="preview__meta">
          加班起算 {{ config.overtimeStart }} · 倍率 ×{{ config.overtimeMultiplier }} ·
          {{ ROUNDING_LABELS[config.roundingRule] }}
        </p>
        <p class="preview__hint">最终金额以管理员确认后的记录为准</p>
      </section>

      <!-- 提交 -->
      <div class="form-actions">
        <button type="button" class="btn btn--secondary" :disabled="submitting" @click="cancel">
          取消
        </button>
        <button type="submit" class="btn btn--primary btn--lg grow" :disabled="submitting">
          <AppIcon v-if="submitting" name="spinner" :size="17" class="spin" />
          {{ submitting ? '提交中…' : isEditing ? '保存并重新提交' : '提交记录' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.form-view {
  min-height: 100dvh;
  background: var(--color-bg);
  padding-bottom: calc(var(--space-2xl) + var(--safe-bottom));
}

.form-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  padding: var(--space-md);
  max-width: 640px;
  margin: 0 auto;
}

@media (min-width: 640px) {
  .form-view {
    max-width: var(--app-max-width);
    margin: 0 auto;
    border-left: 1px solid var(--color-border);
    border-right: 1px solid var(--color-border);
  }
}

/* 区块：主区块用浮起，次区块用描边，制造重量差异 */
.form-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  padding: var(--space-md);
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
}

.form-section__head {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--color-divider);
}

.form-section__head--row {
  flex-direction: row;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-sm);
}

.form-section__title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-snug);
  color: var(--color-fg);
}

/* 工作类型选择：改为横向列表，选中项左侧竖条 */
.type-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.type-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-left: 3px solid transparent;
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  text-align: left;
  transition: all var(--duration-fast) var(--ease-in-out);
  color: var(--color-fg);
}

.type-chip:hover {
  border-color: var(--color-border-strong);
  border-left-color: var(--color-border-strong);
  background: var(--color-surface-muted);
}

.type-chip--active {
  border-color: var(--color-accent);
  border-left-color: var(--color-accent);
  background: var(--color-info-bg);
}

.type-chip__name {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  line-height: 1.3;
}

.type-chip--active .type-chip__name {
  font-weight: var(--weight-semibold);
}

.type-chip__mode {
  font-size: var(--text-2xs);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wide);
  color: var(--color-fg-tertiary);
  padding: 1px 5px;
  border-radius: var(--radius-xs);
  background: var(--color-surface-muted);
  flex: 0 0 auto;
}

.type-chip--active .type-chip__mode {
  color: var(--color-accent);
  background: var(--color-surface);
}

/* 时间 */
.time-presets {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.time-preset {
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: var(--color-surface-muted);
  color: var(--color-fg-secondary);
  font-size: var(--text-xs);
  font-weight: 500;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.time-preset:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-surface);
}

.time-row {
  display: flex;
  align-items: flex-end;
  gap: var(--space-sm);
}

.time-row--single .time-input {
  flex: 1 1 auto;
}

.time-input {
  flex: 1 1 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.time-input__label {
  font-size: 11px;
  color: var(--color-fg-tertiary);
}

.time-sep {
  padding-bottom: 12px;
  color: var(--color-fg-tertiary);
  font-size: var(--text-sm);
}

/* 定位 */
.geo-box {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 8px 10px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-surface-muted);
  font-size: var(--text-xs);
  color: var(--color-fg-secondary);
}

.geo-box--ok {
  border-color: var(--color-success-border);
  background: var(--color-success-bg);
  color: var(--color-success);
}

.geo-box--failed {
  border-color: var(--color-warning-border);
  background: var(--color-warning-bg);
  color: var(--color-warning);
}

.geo-box__text {
  line-height: var(--leading-normal);
  word-break: break-all;
}

/* 字数统计 */
.field__foot {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-sm);
}

.field__counter {
  font-size: 11px;
  color: var(--color-fg-tertiary);
  flex: 0 0 auto;
}

/* 预估 */
.preview {
  border-color: var(--color-info-border);
  background: var(--color-info-bg);
}

.preview__tag {
  margin-left: auto;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: var(--color-accent);
  color: var(--color-accent-fg);
}

.preview__explain {
  font-size: var(--text-xs);
  line-height: var(--leading-relaxed);
  color: var(--color-fg-secondary);
  word-break: break-word;
}

.preview__total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-top: var(--space-sm);
  border-top: 1px dashed var(--color-info-border);
}

.preview__total-label {
  font-size: var(--text-sm);
  color: var(--color-fg-secondary);
}

.preview__total-amount {
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--color-info);
  letter-spacing: -0.02em;
}

.preview__meta {
  font-size: 11px;
  color: var(--color-fg-tertiary);
}

.preview__hint {
  font-size: 11px;
  color: var(--color-fg-tertiary);
  font-style: italic;
}

/* 操作 */
.form-actions {
  display: flex;
  gap: var(--space-sm);
  padding: var(--space-sm) 0 0;
  margin-top: var(--space-xs);
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
