/**
 * Toast 轻提示
 */
import { ref } from 'vue'

export type ToastType = 'info' | 'success' | 'error'

export interface ToastItem {
  id: number
  type: ToastType
  message: string
}

const items = ref<ToastItem[]>([])
let seq = 0

function push(type: ToastType, message: string, duration = 2600): void {
  const id = ++seq
  items.value.push({ id, type, message })
  window.setTimeout(() => {
    items.value = items.value.filter((t) => t.id !== id)
  }, duration)
}

export function useToast() {
  return {
    items,
    show: (message: string) => push('info', message),
    success: (message: string) => push('success', message),
    error: (message: string) => push('error', message, 3600),
    dismiss: (id: number) => {
      items.value = items.value.filter((t) => t.id !== id)
    },
  }
}
