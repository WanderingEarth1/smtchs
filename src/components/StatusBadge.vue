<script setup lang="ts">
/**
 * 记录状态徽标
 *
 * 形状策略（避免所有徽标都是胶囊）：
 * - 状态：圆点 + 文字（最轻，不抢视觉）
 * - 补录：左侧竖条标签（警示感，与状态区分）
 */
import type { RecordStatus } from '@/core/types'

defineProps<{
  status: RecordStatus
  isBackfill?: boolean
}>()

const STATUS_MAP: Record<RecordStatus, { label: string; cls: string }> = {
  PENDING: { label: '待确认', cls: 'dot-label--pending' },
  APPROVED: { label: '已确认', cls: 'dot-label--approved' },
  REJECTED: { label: '已驳回', cls: 'dot-label--rejected' },
}
</script>

<template>
  <span class="badge-group">
    <span class="dot-label" :class="STATUS_MAP[status].cls">
      {{ STATUS_MAP[status].label }}
    </span>
    <span v-if="isBackfill" class="flag-label flag-label--backfill">补录</span>
  </span>
</template>

<style scoped>
.badge-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.dot-label--pending {
  color: var(--color-warning);
}
.dot-label--approved {
  color: var(--color-success);
}
.dot-label--rejected {
  color: var(--color-danger);
}

.flag-label--backfill {
  color: var(--color-info);
}
</style>
