<script setup lang="ts">
/**
 * 照片上传 —— 必拍 1 张，最多 3 张，前端压缩
 */
import { ref } from 'vue'
import type { PhotoRef } from '@/core/types'
import { compressPhoto } from '@/core/photo'
import { MAX_PHOTOS } from '@/data/service'
import { useToast } from '@/stores/toast'
import AppIcon from './AppIcon.vue'

const props = defineProps<{
  modelValue: PhotoRef[]
  error?: string
  disabled?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [PhotoRef[]] }>()

const toast = useToast()
const inputRef = ref<HTMLInputElement | null>(null)
const compressing = ref(false)
const previewIndex = ref<number | null>(null)

function openPicker(): void {
  if (props.disabled) return
  if (props.modelValue.length >= MAX_PHOTOS) {
    toast.show(`最多上传 ${MAX_PHOTOS} 张照片`)
    return
  }
  inputRef.value?.click()
}

async function onFiles(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (files.length === 0) return

  const remain = MAX_PHOTOS - props.modelValue.length
  const accepted = files.slice(0, remain)
  if (files.length > remain) {
    toast.show(`最多上传 ${MAX_PHOTOS} 张，已保留前 ${remain} 张`)
  }

  compressing.value = true
  const next = [...props.modelValue]
  try {
    for (const file of accepted) {
      const photo = await compressPhoto(file)
      next.push(photo)
    }
    emit('update:modelValue', next)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '照片处理失败')
  } finally {
    compressing.value = false
  }
}

function remove(index: number): void {
  if (props.disabled) return
  const next = props.modelValue.filter((_, i) => i !== index)
  emit('update:modelValue', next)
}

function formatSize(bytes: number): string {
  return bytes >= 1024 ? `${Math.round(bytes / 1024)}KB` : `${bytes}B`
}
</script>

<template>
  <div class="photo-field">
    <div class="photo-grid">
      <div v-for="(photo, index) in modelValue" :key="photo.id" class="photo-item">
        <button
          type="button"
          class="photo-item__preview"
          :aria-label="`查看第 ${index + 1} 张照片`"
          @click="previewIndex = index"
        >
          <img :src="photo.dataUrl" :alt="`现场凭证 ${index + 1}`" loading="lazy" />
        </button>
        <span class="photo-item__size">{{ formatSize(photo.size) }}</span>
        <button
          v-if="!disabled"
          type="button"
          class="photo-item__remove"
          :aria-label="`删除第 ${index + 1} 张照片`"
          @click="remove(index)"
        >
          <AppIcon name="x" :size="14" :stroke-width="2.6" />
        </button>
      </div>

      <button
        v-if="modelValue.length < MAX_PHOTOS"
        type="button"
        class="photo-add"
        :disabled="disabled || compressing"
        :aria-invalid="!!error"
        @click="openPicker"
      >
        <AppIcon :name="compressing ? 'spinner' : 'camera'" :size="22" :class="{ spin: compressing }" />
        <span class="photo-add__text">{{ compressing ? '压缩中' : '拍照/上传' }}</span>
        <span class="photo-add__count">{{ modelValue.length }}/{{ MAX_PHOTOS }}</span>
      </button>
    </div>

    <input
      ref="inputRef"
      type="file"
      accept="image/*"
      capture="environment"
      multiple
      class="sr-only"
      tabindex="-1"
      aria-hidden="true"
      @change="onFiles"
    />

    <p v-if="error" class="field__error">
      <AppIcon name="warning" :size="12" :stroke-width="2.4" />
      {{ error }}
    </p>
    <p v-else class="field__hint">必拍 1 张现场凭证，最多 3 张；自动压缩至 300KB 以内</p>

    <!-- 大图预览 -->
    <Teleport to="body">
      <div
        v-if="previewIndex !== null"
        class="photo-viewer"
        role="dialog"
        aria-modal="true"
        aria-label="照片预览"
        @click="previewIndex = null"
      >
        <img :src="modelValue[previewIndex]?.dataUrl" alt="现场凭证大图" />
        <button
          type="button"
          class="photo-viewer__close"
          aria-label="关闭预览"
          @click="previewIndex = null"
        >
          <AppIcon name="x" :size="22" />
        </button>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.photo-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.photo-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.photo-item {
  position: relative;
  width: 92px;
  height: 92px;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--color-border);
  background: var(--color-surface-muted);
}

.photo-item__preview {
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  background: none;
  display: block;
}

.photo-item__preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.photo-item__size {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 2px 6px;
  font-size: 10px;
  color: #fff;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.65));
  font-variant-numeric: tabular-nums;
}

.photo-item__remove {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 50%;
  background: rgba(2, 6, 23, 0.72);
  color: #fff;
  padding: 0;
}

.photo-add {
  width: 92px;
  height: 92px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border: 1.5px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted);
  color: var(--color-fg-secondary);
  transition:
    border-color var(--duration-fast) var(--ease-in-out),
    background-color var(--duration-fast) var(--ease-in-out);
}

.photo-add:hover:not(:disabled) {
  border-color: var(--color-accent);
  background: var(--color-surface);
  color: var(--color-accent);
}

.photo-add[aria-invalid='true'] {
  border-color: var(--color-danger);
}

.photo-add__text {
  font-size: 11px;
  font-weight: 500;
}

.photo-add__count {
  font-size: 10px;
  color: var(--color-fg-tertiary);
  font-variant-numeric: tabular-nums;
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
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
