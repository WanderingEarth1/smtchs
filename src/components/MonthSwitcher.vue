<script setup lang="ts">
/**
 * 月份切换器 —— 用于记录列表 / 报表
 */
import { computed } from 'vue'
import { currentMonthKey, formatMonthLabel, shiftMonth } from '@/core/date'
import AppIcon from './AppIcon.vue'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()

const canGoNext = computed(() => props.modelValue < currentMonthKey())

function prev(): void {
  emit('update:modelValue', shiftMonth(props.modelValue, -1))
}
function next(): void {
  if (!canGoNext.value) return
  emit('update:modelValue', shiftMonth(props.modelValue, 1))
}
</script>

<template>
  <div class="month-switch" role="group" aria-label="切换月份">
    <button class="month-switch__btn" type="button" aria-label="上一个月" @click="prev">
      <AppIcon name="chevron-left" :size="18" />
    </button>
    <span class="month-switch__label tabular">{{ formatMonthLabel(modelValue) }}</span>
    <button
      class="month-switch__btn"
      type="button"
      aria-label="下一个月"
      :disabled="!canGoNext"
      @click="next"
    >
      <AppIcon name="chevron-right" :size="18" />
    </button>
  </div>
</template>

<style scoped>
.month-switch {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  padding: 2px;
}

.month-switch__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--color-fg-secondary);
  transition: background-color var(--duration-fast) var(--ease-in-out);
}

.month-switch__btn:hover:not(:disabled) {
  background: var(--color-surface-muted);
  color: var(--color-fg);
}

.month-switch__btn:disabled {
  opacity: 0.35;
}

.month-switch__label {
  font-size: var(--text-sm);
  font-weight: 600;
  padding: 0 var(--space-sm);
  min-width: 96px;
  text-align: center;
}
</style>
