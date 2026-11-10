<script setup lang="ts">
/**
 * 空状态 —— 引导用户下一步操作（UX: Empty States）
 */
import AppIcon from './AppIcon.vue'
import type { IconName } from './icons'

withDefaults(
  defineProps<{
    icon?: IconName
    title: string
    desc?: string
  }>(),
  { icon: 'inbox' },
)
</script>

<template>
  <div class="empty-state">
    <AppIcon :name="icon" :size="40" :stroke-width="1.4" class="empty-state__icon" />
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="desc" class="empty-state__desc">{{ desc }}</p>
    <slot />
  </div>
</template>
