<script setup lang="ts">
/**
 * 计费规则配置（CFG-04 / CFG-05）
 * 加班起算时间 + 倍率 + 时长取整规则
 */
import { computed, reactive, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { useAuth } from '@/stores/app'
import { useToast } from '@/stores/toast'
import { BizError, getConfig, updateConfig } from '@/data/service'
import { ROUNDING_LABELS, calculateBilling } from '@/core/billing'
import { formatMoney } from '@/core/date'
import type { RoundingRule } from '@/core/types'

const { dataVersion, bumpDataVersion } = useAuth()
const toast = useToast()

const saved = computed(() => {
  void dataVersion.value
  return getConfig()
})

const form = reactive({
  overtimeStart: '18:00',
  overtimeMultiplier: 2,
  roundingRule: 'EXACT' as RoundingRule,
})

const errorMessage = ref('')
const savedFlash = ref(false)

// 同步已保存配置到表单
watch(
  saved,
  (cfg) => {
    form.overtimeStart = cfg.overtimeStart
    form.overtimeMultiplier = cfg.overtimeMultiplier
    form.roundingRule = cfg.roundingRule
  },
  { immediate: true },
)

const isDirty = computed(
  () =>
    form.overtimeStart !== saved.value.overtimeStart ||
    form.overtimeMultiplier !== saved.value.overtimeMultiplier ||
    form.roundingRule !== saved.value.roundingRule,
)

const roundingOptions: Array<{ key: RoundingRule; desc: string }> = [
  { key: 'EXACT', desc: '按实际分钟数计费，不做补足' },
  { key: 'HALF_HOUR_UP', desc: '不足半小时按半小时计，如 1h40m → 2h' },
  { key: 'HOUR_UP', desc: '不足一小时按一小时计，如 1h10m → 2h' },
]

/** 实时预览：用文档示例 1/2 验证规则效果 */
const previews = computed(() => {
  const cases = [
    { label: '17:30 ~ 19:30', start: '17:30', end: '19:30' },
    { label: '17:30 ~ 19:40', start: '17:30', end: '19:40' },
    { label: '09:00 ~ 12:00', start: '09:00', end: '12:00' },
  ]
  return cases.map((c) => {
    try {
      const r = calculateBilling({
        billingMode: 'BY_HOUR',
        unitPrice: 80,
        startTime: c.start,
        endTime: c.end,
        overtimeStart: form.overtimeStart,
        overtimeMultiplier: form.overtimeMultiplier,
        roundingRule: form.roundingRule,
      })
      return { ...c, result: r }
    } catch {
      return { ...c, result: null }
    }
  })
})

function save(): void {
  errorMessage.value = ''
  try {
    updateConfig({
      overtimeStart: form.overtimeStart,
      overtimeMultiplier: Number(form.overtimeMultiplier),
      roundingRule: form.roundingRule,
    })
    bumpDataVersion()
    toast.success('计费规则已保存，对新提交的记录生效')
    savedFlash.value = true
    window.setTimeout(() => (savedFlash.value = false), 1600)
  } catch (err) {
    errorMessage.value = err instanceof BizError ? err.message : '保存失败'
  }
}

function reset(): void {
  form.overtimeStart = saved.value.overtimeStart
  form.overtimeMultiplier = saved.value.overtimeMultiplier
  form.roundingRule = saved.value.roundingRule
  errorMessage.value = ''
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-head__title">计费规则</h1>
        <p class="page-head__desc">加班起算时间、加班倍率与时长取整规则 · 全局生效</p>
      </div>
      <div class="page-head__actions">
        <RouterLink to="/a/config" class="btn btn--ghost btn--sm">
          <AppIcon name="chevron-left" :size="15" />
          返回配置
        </RouterLink>
      </div>
    </div>

    <div class="alert alert--warning mb-md">
      <AppIcon name="warning" :size="16" />
      <span>
        修改后<strong>仅对新提交的记录生效</strong>。已提交记录按提交时固化的规则核算，保证历史口径不变。
      </span>
    </div>

    <div class="rules-grid">
      <div class="rules-form">
        <div v-if="errorMessage" class="alert alert--danger mb-md">
          <AppIcon name="warning" :size="16" />
          <span>{{ errorMessage }}</span>
        </div>

        <!-- 加班起算时间 -->
        <section class="card card--pad">
          <h2 class="section-title">
            <AppIcon name="clock" :size="15" />
            加班起算时间
          </h2>
          <p class="rules-desc">
            按小时计费的记录，结束时间超过该时刻的部分计入加班时段
          </p>
          <div class="field mt-sm">
            <label class="field__label" for="ot-start">起算时间</label>
            <input
              id="ot-start"
              v-model="form.overtimeStart"
              class="input tabular"
              type="time"
              style="max-width: 180px"
            />
            <p class="field__hint">默认 18:00，即 18 点后按加班倍率计算</p>
          </div>
        </section>

        <!-- 加班倍率 -->
        <section class="card card--pad">
          <h2 class="section-title">
            <AppIcon name="trend-up" :size="15" />
            加班倍率
          </h2>
          <p class="rules-desc">加班时段金额 = 加班时长 × 单价 × 倍率</p>
          <div class="field mt-sm">
            <label class="field__label" for="ot-mult">倍率</label>
            <div class="mult-row">
              <input
                id="ot-mult"
                v-model.number="form.overtimeMultiplier"
                class="input tabular"
                type="number"
                inputmode="decimal"
                min="1"
                max="10"
                step="0.1"
                style="max-width: 140px"
              />
              <span class="mult-preview">× {{ form.overtimeMultiplier }}</span>
            </div>
            <p class="field__hint">默认 2 倍，取值范围 1 ~ 10</p>
          </div>
        </section>

        <!-- 取整规则 -->
        <section class="card card--pad">
          <h2 class="section-title">
            <AppIcon name="settings" :size="15" />
            时长取整规则
          </h2>
          <p class="rules-desc">
            正常段与加班段<strong>分别</strong>按此规则向上补足（约定 8）
          </p>
          <div class="rounding-options mt-sm" role="radiogroup" aria-label="选择取整规则">
            <button
              v-for="opt in roundingOptions"
              :key="opt.key"
              type="button"
              role="radio"
              class="rounding-option"
              :aria-checked="form.roundingRule === opt.key"
              :class="{ 'rounding-option--active': form.roundingRule === opt.key }"
              @click="form.roundingRule = opt.key"
            >
              <span class="rounding-option__radio" aria-hidden="true">
                <span v-if="form.roundingRule === opt.key" class="rounding-option__dot" />
              </span>
              <span class="grow">
                <span class="rounding-option__title">{{ ROUNDING_LABELS[opt.key] }}</span>
                <span class="rounding-option__desc">{{ opt.desc }}</span>
              </span>
            </button>
          </div>
        </section>

        <div class="rules-actions">
          <button type="button" class="btn btn--secondary" :disabled="!isDirty" @click="reset">
            重置
          </button>
          <button type="button" class="btn btn--primary grow" :disabled="!isDirty" @click="save">
            <AppIcon v-if="savedFlash" name="check" :size="16" :stroke-width="2.6" />
            保存规则
          </button>
        </div>
      </div>

      <!-- 实时预览 -->
      <aside class="rules-preview">
        <section class="card card--pad">
          <h2 class="section-title">
            <AppIcon name="eye" :size="15" />
            规则实时预览
          </h2>
          <p class="rules-desc">
            以「维修 · 按小时 · 单价 ¥80/小时」为例，验证当前规则下的核算结果
          </p>

          <div class="preview-cases">
            <div v-for="c in previews" :key="c.label" class="preview-case">
              <div class="row row--between">
                <span class="preview-case__label tabular">{{ c.label }}</span>
                <span class="preview-case__amount tabular">
                  {{ c.result ? `¥${formatMoney(c.result.total)}` : '—' }}
                </span>
              </div>
              <p v-if="c.result" class="preview-case__detail tabular">
                正常 {{ c.result.normalHours }}h × 80 = ¥{{ formatMoney(c.result.normalAmount) }}
                <template v-if="c.result.overtimeHours > 0">
                  · 加班 {{ c.result.overtimeHours }}h × 80 × {{ form.overtimeMultiplier }} = ¥{{
                    formatMoney(c.result.overtimeAmount)
                  }}
                </template>
              </p>
            </div>
          </div>

          <div class="preview-note">
            <AppIcon name="info" :size="14" />
            <span>
              当前规则：{{ form.overtimeStart }} 起算 · ×{{ form.overtimeMultiplier }} ·
              {{ ROUNDING_LABELS[form.roundingRule] }}
            </span>
          </div>
        </section>

        <section class="card card--pad">
          <h2 class="section-title">
            <AppIcon name="shield" :size="15" />
            核算口径说明
          </h2>
          <ul class="rules-list">
            <li><strong>按次</strong>记录不参与加班与取整，一条记录 = 1 次。</li>
            <li>
              <strong>按小时</strong>记录以加班起算时间为界切分：正常段 T₁、加班段 T₂ 分别取整后计费。
            </li>
            <li>开始时间已晚于起算点时，全部时长计入加班段。</li>
            <li>结束时间早于起算点时，全部时长计入正常段。</li>
            <li>跨 0 点的连续服务时长归入加班段连续计算。</li>
          </ul>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.mb-md {
  margin-bottom: var(--space-md);
}

.rules-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-md);
  align-items: start;
}

@media (min-width: 1000px) {
  .rules-grid {
    grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  }
}

.rules-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.rules-preview {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  position: sticky;
  top: 80px;
}

@media (max-width: 999px) {
  .rules-preview {
    position: static;
  }
}

.rules-desc {
  font-size: var(--text-xs);
  color: var(--color-fg-tertiary);
  line-height: var(--leading-relaxed);
  margin-top: 4px;
}

.mult-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.mult-preview {
  font-size: var(--text-md);
  font-weight: 700;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
}

.rounding-options {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.rounding-option {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  padding: 10px var(--space-md);
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  text-align: left;
  transition: all var(--duration-fast) var(--ease-in-out);
  color: var(--color-fg);
}

.rounding-option:hover {
  border-color: var(--color-border-strong);
  background: var(--color-surface-muted);
}

.rounding-option--active {
  border-color: var(--color-accent);
  background: var(--color-info-bg);
}

.rounding-option__radio {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  margin-top: 2px;
  border: 2px solid var(--color-border-strong);
  border-radius: 50%;
  background: var(--color-surface);
}

.rounding-option--active .rounding-option__radio {
  border-color: var(--color-accent);
}

.rounding-option__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-accent);
}

.rounding-option__title {
  display: block;
  font-size: var(--text-sm);
  font-weight: 600;
}

.rounding-option__desc {
  display: block;
  font-size: var(--text-xs);
  color: var(--color-fg-tertiary);
  margin-top: 1px;
}

.rules-actions {
  display: flex;
  gap: var(--space-sm);
  position: sticky;
  bottom: var(--space-md);
  padding: var(--space-sm);
  background: color-mix(in srgb, var(--color-bg) 92%, transparent);
  backdrop-filter: blur(10px);
  border-radius: var(--radius-lg);
}

.preview-cases {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}

.preview-case {
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted);
}

.preview-case__label {
  font-size: var(--text-sm);
  font-weight: 600;
}

.preview-case__amount {
  font-size: var(--text-md);
  font-weight: 700;
  color: var(--color-accent);
}

.preview-case__detail {
  font-size: 11px;
  color: var(--color-fg-tertiary);
  margin-top: 2px;
  line-height: var(--leading-relaxed);
}

.preview-note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px dashed var(--color-border);
  font-size: 11px;
  color: var(--color-fg-tertiary);
}

.rules-list {
  margin: var(--space-sm) 0 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: var(--text-xs);
  color: var(--color-fg-secondary);
  line-height: var(--leading-relaxed);
}

.rules-list strong {
  color: var(--color-fg);
}
</style>
