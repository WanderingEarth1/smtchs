<script setup lang="ts">
/**
 * 管理端移动布局
 *
 * 按需求：移动端只提供「查看详细记录数据、审核、金额汇总」三项能力，
 * 不含配置管理（员工/类型/单价/规则）与 Excel 导出。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import type { IconName } from '@/components/icons'
import { useAuth, useTheme } from '@/stores/app'
import { countPending } from '@/data/service'

const route = useRoute()
const router = useRouter()
const { currentUser, dataVersion } = useAuth()
const { theme, toggle } = useTheme()

const tabs: Array<{ name: string; label: string; icon: IconName; to: string }> = [
  { name: 'summary', label: '汇总', icon: 'chart', to: '/a/m/summary' },
  { name: 'records', label: '记录', icon: 'list', to: '/a/m/records' },
  { name: 'review', label: '审核', icon: 'check-circle', to: '/a/m/review' },
]

const activeTab = computed(() => (route.meta.tab as string) ?? 'summary')

const pendingCount = ref(0)

/** 加载待审核数量（用于角标） */
async function loadPendingCount(): Promise<void> {
  try {
    pendingCount.value = await countPending()
  } catch {
    pendingCount.value = 0
  }
}

watch([dataVersion, () => route.path], () => {
  void loadPendingCount()
})

onMounted(() => {
  void loadPendingCount()
})

function go(to: string): void {
  if (route.path !== to) void router.push(to)
}
</script>

<template>
  <div class="m-shell">
    <main class="m-main">
      <RouterView v-slot="{ Component }">
        <Transition name="slide" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>

    <nav class="m-tabbar" aria-label="管理端导航">
      <button
        v-for="tab in tabs"
        :key="tab.name"
        type="button"
        class="m-tabbar__item"
        :aria-current="activeTab === tab.name ? 'page' : undefined"
        @click="go(tab.to)"
      >
        <span class="m-tabbar__icon">
          <AppIcon :name="tab.icon" :size="21" :stroke-width="activeTab === tab.name ? 2.2 : 1.8" />
          <span
            v-if="tab.name === 'review' && pendingCount > 0"
            class="m-tabbar__badge"
          >
            {{ pendingCount > 99 ? '99+' : pendingCount }}
          </span>
        </span>
        <span>{{ tab.label }}</span>
      </button>
    </nav>

    <span class="sr-only">当前登录：{{ currentUser?.name }}（管理员）</span>
  </div>
</template>

<style scoped>
.m-shell {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
}

@media (min-width: 640px) {
  .m-shell {
    max-width: var(--app-max-width);
    margin: 0 auto;
    border-left: 1px solid var(--color-border);
    border-right: 1px solid var(--color-border);
    box-shadow: 0 0 60px rgba(15, 23, 42, 0.06);
  }
}

.m-main {
  flex: 1 1 auto;
  padding-bottom: calc(var(--tabbar-height) + var(--safe-bottom));
}

.m-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  display: flex;
  align-items: stretch;
  height: calc(var(--tabbar-height) + var(--safe-bottom));
  padding-bottom: var(--safe-bottom);
  background: color-mix(in srgb, var(--color-bg-elevated) 94%, transparent);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-top: 1px solid var(--color-border);
}

@media (min-width: 640px) {
  .m-tabbar {
    max-width: var(--app-max-width);
    left: 50%;
    transform: translateX(-50%);
    border-left: 1px solid var(--color-border);
    border-right: 1px solid var(--color-border);
  }
}

.m-tabbar__item {
  flex: 1 1 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border: 0;
  background: transparent;
  color: var(--color-fg-tertiary);
  font-size: 11px;
  font-weight: 500;
  min-height: 48px;
  transition: color var(--duration-fast) var(--ease-in-out);
}

.m-tabbar__item[aria-current='page'] {
  color: var(--color-accent);
  font-weight: 600;
}

.m-tabbar__item:active {
  background: var(--color-surface-muted);
}

.m-tabbar__icon {
  position: relative;
  display: inline-flex;
}

.m-tabbar__badge {
  position: absolute;
  top: -4px;
  left: 12px;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  border-radius: var(--radius-full);
  background: var(--color-danger);
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  line-height: 15px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
</style>
