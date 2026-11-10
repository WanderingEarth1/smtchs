<script setup lang="ts">
/**
 * Toast 容器 —— 全局挂载一次
 */
import { useToast } from '@/stores/toast'
import AppIcon from './AppIcon.vue'

const { items, dismiss } = useToast()
</script>

<template>
  <Teleport to="body">
    <div class="toast-stack" role="status" aria-live="polite" aria-atomic="false">
      <TransitionGroup name="toast">
        <div
          v-for="item in items"
          :key="item.id"
          class="toast"
          :class="`toast--${item.type}`"
          @click="dismiss(item.id)"
        >
          <AppIcon
            :name="
              item.type === 'success' ? 'check-circle' : item.type === 'error' ? 'x-circle' : 'info'
            "
            :size="18"
          />
          <span class="grow">{{ item.message }}</span>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition:
    opacity var(--duration-base) var(--ease-out),
    transform var(--duration-base) var(--ease-out);
}
.toast-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}
.toast-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
