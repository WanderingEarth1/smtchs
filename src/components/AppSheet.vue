<script setup lang="ts">
/**
 * 底部弹层 / 居中对话框
 * 移动端从底部升起，桌面端居中显示（见 components.css .sheet）
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    /** 是否显示底部按钮区 */
    showFooter?: boolean
    /** 点击遮罩是否关闭 */
    dismissible?: boolean
  }>(),
  { showFooter: true, dismissible: true },
)

const emit = defineEmits<{ close: [] }>()

const panel = ref<HTMLElement | null>(null)
let lastFocused: HTMLElement | null = null

function close(): void {
  if (props.dismissible) emit('close')
}

function onKeydown(e: KeyboardEvent): void {
  if (!props.open) return
  if (e.key === 'Escape') {
    e.preventDefault()
    close()
    return
  }
  // 焦点陷阱
  if (e.key === 'Tab' && panel.value) {
    const focusables = panel.value.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    )
    if (focusables.length === 0) return
    const first = focusables[0]
    const last = focusables[focusables.length - 1]
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault()
      last.focus()
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault()
      first.focus()
    }
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      lastFocused = document.activeElement as HTMLElement
      document.body.style.overflow = 'hidden'
      requestAnimationFrame(() => {
        const target = panel.value?.querySelector<HTMLElement>(
          'input:not([disabled]), textarea:not([disabled]), select:not([disabled]), button',
        )
        target?.focus()
      })
    } else {
      document.body.style.overflow = ''
      lastFocused?.focus?.()
    }
  },
)

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="sheet-backdrop" @click="close" />
    </Transition>
    <Transition name="sheet">
      <div
        v-if="open"
        ref="panel"
        class="sheet"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
      >
        <div class="sheet__handle" />
        <header class="sheet__header">
          <h2 class="sheet__title">{{ title }}</h2>
          <button class="btn btn--ghost btn--icon" type="button" aria-label="关闭" @click="close">
            <AppIcon name="x" :size="20" />
          </button>
        </header>
        <div class="sheet__body">
          <slot />
        </div>
        <footer v-if="showFooter" class="sheet__footer">
          <slot name="footer" />
        </footer>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.sheet-enter-active,
.sheet-leave-active {
  transition:
    transform var(--duration-slow) var(--ease-out),
    opacity var(--duration-base) var(--ease-out);
}

.sheet-enter-from,
.sheet-leave-to {
  transform: translateY(24px);
  opacity: 0;
}

@media (min-width: 640px) {
  .sheet-enter-from,
  .sheet-leave-to {
    transform: translate(-50%, -46%) scale(0.97);
  }
}
</style>
