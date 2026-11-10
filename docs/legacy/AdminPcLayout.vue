<script setup lang="ts">
/**
 * 管理端 PC 布局 —— 若依（RuoYi）框架风格
 *
 * 结构对齐若依：
 * - 左侧深色侧边栏（Logo + 折叠菜单）
 * - 顶部导航条（面包屑 + 右侧用户区）
 * - 内容区浅灰底 + 白色卡片
 * - 支持侧边栏折叠
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import type { IconName } from '@/components/icons'
import { useAuth, useTheme } from '@/stores/app'
import { countPending } from '@/data/service'

const route = useRoute()
const router = useRouter()
const { currentUser, logout, dataVersion } = useAuth()
const { theme, toggle } = useTheme()

interface MenuItem {
  name: string
  label: string
  icon: IconName
  to: string
  badge?: boolean
}

interface MenuGroup {
  key: string
  label: string
  icon: IconName
  children: MenuItem[]
}

const menuGroups: MenuGroup[] = [
  {
    key: 'dashboard',
    label: '概览',
    icon: 'chart',
    children: [{ name: 'dashboard', label: '经营概览', icon: 'chart', to: '/a/pc/dashboard' }],
  },
  {
    key: 'review',
    label: '审核管理',
    icon: 'check-circle',
    children: [
      { name: 'review', label: '记录审核', icon: 'check-circle', to: '/a/pc/review', badge: true },
    ],
  },
  {
    key: 'reports',
    label: '统计报表',
    icon: 'file-text',
    children: [{ name: 'reports', label: '月度报表', icon: 'file-text', to: '/a/pc/reports' }],
  },
  {
    key: 'config',
    label: '系统配置',
    icon: 'settings',
    children: [
      { name: 'employees', label: '员工管理', icon: 'users', to: '/a/pc/config/employees' },
      { name: 'worktypes', label: '工作类型', icon: 'tag', to: '/a/pc/config/work-types' },
      { name: 'prices', label: '单价矩阵', icon: 'money', to: '/a/pc/config/prices' },
      { name: 'rules', label: '计费规则', icon: 'settings', to: '/a/pc/config/rules' },
    ],
  },
]

const collapsed = ref(false)
const openGroups = ref<Set<string>>(new Set(['dashboard', 'review', 'reports', 'config']))
const userMenuOpen = ref(false)

const pendingCount = computed(() => {
  void dataVersion.value
  return countPending()
})

const activePath = computed(() => route.path)

/** 当前页面对应的菜单项与分组 */
const currentMenu = computed(() => {
  for (const g of menuGroups) {
    for (const c of g.children) {
      if (activePath.value === c.to) return { group: g, item: c }
    }
  }
  return null
})

const breadcrumbs = computed(() => {
  const cur = currentMenu.value
  if (!cur) return [{ label: '首页', to: '/a/pc/dashboard' }]
  const list = [{ label: '首页', to: '/a/pc/dashboard' }]
  if (cur.group.key !== 'dashboard') list.push({ label: cur.group.label, to: '' })
  list.push({ label: cur.item.label, to: cur.item.to })
  return list
})

function toggleGroup(key: string): void {
  const next = new Set(openGroups.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  openGroups.value = next
}

function go(to: string): void {
  userMenuOpen.value = false
  if (route.path !== to) void router.push(to)
}

function doLogout(): void {
  logout()
  void router.replace('/login')
}

function onDocClick(e: MouseEvent): void {
  const t = e.target as HTMLElement
  if (!t.closest('.pc-user')) userMenuOpen.value = false
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <div class="pc-shell" :class="{ 'pc-shell--collapsed': collapsed }">
    <!-- 侧边栏 -->
    <aside class="pc-sidebar">
      <div class="pc-logo">
        <span class="pc-logo__mark">
          <AppIcon name="briefcase" :size="18" :stroke-width="2" />
        </span>
        <span v-show="!collapsed" class="pc-logo__text">服务记录与提成核算</span>
      </div>

      <nav class="pc-menu" aria-label="管理端主菜单">
        <div v-for="group in menuGroups" :key="group.key" class="pc-menu__group">
          <!-- 单页分组直接渲染为菜单项 -->
          <button
            v-if="group.children.length === 1"
            type="button"
            class="pc-menu__item"
            :class="{ 'pc-menu__item--active': activePath === group.children[0].to }"
            :title="collapsed ? group.label : undefined"
            @click="go(group.children[0].to)"
          >
            <AppIcon :name="group.icon" :size="17" class="pc-menu__icon" />
            <span v-show="!collapsed" class="pc-menu__label">{{ group.label }}</span>
            <span
              v-if="group.children[0].badge && pendingCount > 0"
              class="pc-menu__badge"
              :class="{ 'pc-menu__badge--dot': collapsed }"
            >
              {{ collapsed ? '' : pendingCount > 99 ? '99+' : pendingCount }}
            </span>
          </button>

          <!-- 多页分组：可折叠 -->
          <template v-else>
            <button
              type="button"
              class="pc-menu__item pc-menu__item--parent"
              :class="{ 'pc-menu__item--open': openGroups.has(group.key) }"
              :title="collapsed ? group.label : undefined"
              @click="toggleGroup(group.key)"
            >
              <AppIcon :name="group.icon" :size="17" class="pc-menu__icon" />
              <span v-show="!collapsed" class="pc-menu__label">{{ group.label }}</span>
              <AppIcon
                v-show="!collapsed"
                name="chevron-down"
                :size="13"
                class="pc-menu__arrow"
                :class="{ 'pc-menu__arrow--open': openGroups.has(group.key) }"
              />
            </button>

            <div v-show="!collapsed && openGroups.has(group.key)" class="pc-submenu">
              <button
                v-for="child in group.children"
                :key="child.name"
                type="button"
                class="pc-submenu__item"
                :class="{ 'pc-submenu__item--active': activePath === child.to }"
                @click="go(child.to)"
              >
                {{ child.label }}
              </button>
            </div>
          </template>
        </div>
      </nav>

      <button
        type="button"
        class="pc-collapse"
        :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="collapsed = !collapsed"
      >
        <AppIcon :name="collapsed ? 'chevron-right' : 'chevron-left'" :size="15" />
      </button>
    </aside>

    <!-- 主区域 -->
    <div class="pc-main">
      <!-- 顶部导航 -->
      <header class="pc-navbar">
        <div class="pc-navbar__left">
          <nav class="pc-breadcrumb" aria-label="面包屑">
            <template v-for="(bc, i) in breadcrumbs" :key="i">
              <button
                v-if="bc.to && i < breadcrumbs.length - 1"
                type="button"
                class="pc-breadcrumb__link"
                @click="go(bc.to)"
              >
                {{ bc.label }}
              </button>
              <span v-else class="pc-breadcrumb__current">{{ bc.label }}</span>
              <span v-if="i < breadcrumbs.length - 1" class="pc-breadcrumb__sep">/</span>
            </template>
          </nav>
        </div>

        <div class="pc-navbar__right">
          <button
            type="button"
            class="pc-icon-btn"
            :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
            @click="toggle"
          >
            <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" :size="17" />
          </button>

          <div class="pc-user">
            <button
              type="button"
              class="pc-user__btn"
              :aria-expanded="userMenuOpen"
              aria-haspopup="menu"
              @click.stop="userMenuOpen = !userMenuOpen"
            >
              <span class="pc-user__avatar">{{ currentUser?.name.slice(0, 1) }}</span>
              <span class="pc-user__name">{{ currentUser?.name }}</span>
              <AppIcon name="chevron-down" :size="13" />
            </button>
            <div v-if="userMenuOpen" class="pc-user__menu" role="menu">
              <div class="pc-user__meta">
                <span class="pc-user__meta-name">{{ currentUser?.name }}</span>
                <span class="pc-user__meta-role">管理员</span>
              </div>
              <button type="button" class="pc-user__menu-item" role="menuitem" @click="doLogout">
                <AppIcon name="logout" :size="15" />
                退出登录
              </button>
            </div>
          </div>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="pc-content">
        <RouterView v-slot="{ Component }">
          <Transition name="fade" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.pc-shell {
  display: flex;
  min-height: 100dvh;
  background: #f0f2f5;
}

:root[data-theme='dark'] .pc-shell {
  background: var(--color-bg);
}

/* ---------- 侧边栏 ---------- */
.pc-sidebar {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 50;
  width: 210px;
  display: flex;
  flex-direction: column;
  background: #304156;
  transition: width var(--duration-base) var(--ease-out);
}

.pc-shell--collapsed .pc-sidebar {
  width: 64px;
}

.pc-logo {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 56px;
  padding: 0 14px;
  flex: 0 0 56px;
  background: #2b3a4d;
  overflow: hidden;
  white-space: nowrap;
}

.pc-logo__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 7px;
  background: linear-gradient(140deg, #0052ff, #4d7cff);
  color: #fff;
}

.pc-logo__text {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 0.01em;
}

.pc-menu {
  flex: 1 1 auto;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 6px 0 60px;
  scrollbar-width: thin;
  scrollbar-color: #4a5c72 transparent;
}

.pc-menu::-webkit-scrollbar {
  width: 6px;
}
.pc-menu::-webkit-scrollbar-thumb {
  background: #4a5c72;
  border-radius: 3px;
}

.pc-menu__group {
  margin-bottom: 1px;
}

.pc-menu__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  height: 44px;
  padding: 0 14px;
  border: 0;
  background: transparent;
  color: #bfcbd9;
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
  transition:
    background-color var(--duration-fast) var(--ease-in-out),
    color var(--duration-fast) var(--ease-in-out);
}

.pc-menu__item:hover {
  background: #263445;
  color: #fff;
}

.pc-menu__item--active {
  background: #1f2d3d;
  color: #fff;
}

.pc-menu__item--active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: #0052ff;
}

.pc-menu__icon {
  flex: 0 0 17px;
  opacity: 0.9;
}

.pc-menu__label {
  flex: 1 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pc-menu__arrow {
  flex: 0 0 auto;
  transition: transform var(--duration-fast) var(--ease-in-out);
  opacity: 0.7;
}

.pc-menu__arrow--open {
  transform: rotate(180deg);
}

.pc-menu__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 9px;
  background: #f56c6c;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  flex: 0 0 auto;
}

.pc-menu__badge--dot {
  position: absolute;
  top: 9px;
  right: 12px;
  min-width: 7px;
  width: 7px;
  height: 7px;
  padding: 0;
  border-radius: 50%;
}

.pc-submenu {
  background: #1f2d3d;
}

.pc-submenu__item {
  display: block;
  width: 100%;
  height: 38px;
  padding: 0 14px 0 40px;
  border: 0;
  background: transparent;
  color: #bfcbd9;
  font-size: 12.5px;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition:
    background-color var(--duration-fast) var(--ease-in-out),
    color var(--duration-fast) var(--ease-in-out);
}

.pc-submenu__item:hover {
  background: #263445;
  color: #fff;
}

.pc-submenu__item--active {
  color: #4d7cff;
  background: #263445;
  font-weight: 500;
}

.pc-collapse {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-top: 1px solid #263445;
  background: #2b3a4d;
  color: #bfcbd9;
  transition: color var(--duration-fast) var(--ease-in-out);
}

.pc-collapse:hover {
  color: #fff;
}

/* ---------- 主区域 ---------- */
.pc-main {
  flex: 1 1 auto;
  min-width: 0;
  margin-left: 210px;
  display: flex;
  flex-direction: column;
  transition: margin-left var(--duration-base) var(--ease-out);
}

.pc-shell--collapsed .pc-main {
  margin-left: 64px;
}

.pc-navbar {
  position: sticky;
  top: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  height: 56px;
  padding: 0 var(--space-md);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  box-shadow: 0 1px 3px rgba(0, 21, 41, 0.06);
}

.pc-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.pc-breadcrumb__link {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  border: 0;
  background: transparent;
  color: var(--color-fg-tertiary);
  font-size: 13px;
  padding: 0 2px;
  border-radius: var(--radius-xs);
  transition:
    color var(--duration-fast) var(--ease-in-out),
    background-color var(--duration-fast) var(--ease-in-out);
}

.pc-breadcrumb__link:hover {
  color: var(--color-accent);
  background: var(--color-accent-soft);
}

.pc-breadcrumb__current {
  color: var(--color-fg);
  font-weight: 500;
}

.pc-breadcrumb__sep {
  color: var(--color-border-strong);
}

.pc-navbar__right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex: 0 0 auto;
}

.pc-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-fg-secondary);
  transition: background-color var(--duration-fast) var(--ease-in-out);
}

.pc-icon-btn:hover {
  background: var(--color-surface-muted);
  color: var(--color-fg);
}

.pc-user {
  position: relative;
}

.pc-user__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 8px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-fg);
  font-size: 13px;
  transition: background-color var(--duration-fast) var(--ease-in-out);
}

.pc-user__btn:hover {
  background: var(--color-surface-muted);
}

.pc-user__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: linear-gradient(140deg, #0052ff, #4d7cff);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.pc-user__menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 170px;
  padding: 4px;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  z-index: 60;
}

.pc-user__meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--color-divider);
  margin-bottom: 4px;
}

.pc-user__meta-name {
  font-size: 13px;
  font-weight: 600;
}

.pc-user__meta-role {
  font-size: 11px;
  color: var(--color-fg-tertiary);
}

.pc-user__menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 36px;
  padding: 0 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-fg);
  font-size: 13px;
  text-align: left;
}

.pc-user__menu-item:hover {
  background: var(--color-surface-muted);
}

.pc-content {
  flex: 1 1 auto;
  padding: var(--space-md);
  min-width: 0;
}

/* 小屏时侧边栏自动收起 */
@media (max-width: 900px) {
  .pc-sidebar {
    width: 64px;
  }
  .pc-main {
    margin-left: 64px;
  }
  .pc-logo__text,
  .pc-menu__label,
  .pc-menu__arrow {
    display: none !important;
  }
  .pc-submenu {
    display: none !important;
  }
}
</style>
