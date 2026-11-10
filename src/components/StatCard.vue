<script setup lang="ts">
/**
 * 统计卡片 —— 用于汇总视图
 */
import AppIcon from './AppIcon.vue'
import type { IconName } from './icons'

withDefaults(
  defineProps<{
    label: string
    value: string
    unit?: string
    icon?: IconName
    tone?: 'default' | 'accent' | 'success' | 'warning'
    hint?: string
  }>(),
  { tone: 'default' },
)
</script>

<template>
  <div class="stat" :class="`stat--${tone}`">
    <div class="stat__head">
      <span class="stat__label">{{ label }}</span>
      <AppIcon v-if="icon" :name="icon" :size="16" class="stat__icon" />
    </div>
    <p class="stat__value tabular">
      {{ value }}<span v-if="unit" class="stat__unit">{{ unit }}</span>
    </p>
    <p v-if="hint" class="stat__hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  min-width: 0;
}

.stat__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-xs);
}

.stat__label {
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--color-fg-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stat__icon {
  color: var(--color-fg-tertiary);
  flex: 0 0 auto;
}

.stat__value {
  font-size: var(--text-xl);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: var(--color-fg);
}

.stat__unit {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-fg-tertiary);
  margin-left: 2px;
}

.stat__hint {
  font-size: var(--text-xs);
  color: var(--color-fg-tertiary);
}

.stat--accent {
  background: linear-gradient(160deg, var(--brand-900), var(--brand-700));
  border-color: transparent;
}
.stat--accent .stat__label,
.stat--accent .stat__icon,
.stat--accent .stat__unit,
.stat--accent .stat__hint {
  color: rgba(255, 255, 255, 0.78);
}
.stat--accent .stat__value {
  color: #fff;
}

.stat--success .stat__value {
  color: var(--color-success);
}
.stat--warning .stat__value {
  color: var(--color-warning);
}
</style>
