<script setup lang="ts">
/**
 * 员工端布局：移动优先 + 底部标签栏
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import type { IconName } from '@/components/icons'
import { useAuth } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const { currentUser } = useAuth()

const tabs: Array<{ name: string; label: string; icon: IconName; to: string }> = [
  { name: 'home', label: '首页', icon: 'chart', to: '/e/home' },
  { name: 'records', label: '我的记录', icon: 'list', to: '/e/records' },
  { name: 'profile', label: '我的', icon: 'user', to: '/e/profile' },
]

const activeTab = computed(() => (route.meta.tab as string) ?? 'home')

function go(to: string): void {
  if (route.path !== to) void router.push(to)
}
</script>

<template>
  <div class="employee-shell">
    <main class="employee-main">
      <RouterView v-slot="{ Component }">
        <Transition name="slide" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>

    <nav class="tabbar" aria-label="主导航">
      <button
        v-for="tab in tabs"
        :key="tab.name"
        type="button"
        class="tabbar__item"
        :aria-current="activeTab === tab.name ? 'page' : undefined"
        @click="go(tab.to)"
      >
        <AppIcon :name="tab.icon" :size="22" :stroke-width="activeTab === tab.name ? 2.2 : 1.8" />
        <span>{{ tab.label }}</span>
      </button>
    </nav>

    <span class="sr-only">当前登录：{{ currentUser?.name }}</span>
  </div>
</template>
