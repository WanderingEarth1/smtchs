<script setup lang="ts">
/**
 * 配置中心入口
 */
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import type { IconName } from '@/components/icons'
import { useAuth } from '@/stores/app'
import { getConfig, listEmployees, listPriceRules, listWorkTypes } from '@/data/service'
import { ROUNDING_LABELS } from '@/core/billing'

const { dataVersion } = useAuth()

const employeeCount = computed(() => {
  void dataVersion.value
  return listEmployees().filter((e) => e.role === 'EMPLOYEE').length
})

const workTypeCount = computed(() => {
  void dataVersion.value
  return listWorkTypes().length
})

const priceCount = computed(() => {
  void dataVersion.value
  return listPriceRules().length
})

const config = computed(() => {
  void dataVersion.value
  return getConfig()
})

const items: Array<{
  to: string
  icon: IconName
  title: string
  desc: () => string
}> = [
  {
    to: '/a/config/employees',
    icon: 'users',
    title: '员工管理',
    desc: () => `${employeeCount.value} 名员工 · 增删改、停用、重置密码`,
  },
  {
    to: '/a/config/work-types',
    icon: 'tag',
    title: '工作类型',
    desc: () => `${workTypeCount.value} 个工作类型 · 设定按次 / 按小时计费模式`,
  },
  {
    to: '/a/config/prices',
    icon: 'money',
    title: '单价矩阵',
    desc: () => `${priceCount.value} 条单价 · 按「工程师 × 工作类型」配置`,
  },
  {
    to: '/a/config/rules',
    icon: 'settings',
    title: '计费规则',
    desc: () =>
      `加班 ${config.value.overtimeStart} 起算 ×${config.value.overtimeMultiplier} · ${ROUNDING_LABELS[config.value.roundingRule]}`,
  },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <p class="t-eyebrow">Settings</p>
        <h1 class="page-head__title">基础配置</h1>
        <p class="page-head__desc">维护员工名单、工作类型、单价矩阵与计费规则</p>
      </div>
    </div>

    <div class="cfg-list">
      <RouterLink
        v-for="(item, idx) in items"
        :key="item.to"
        :to="item.to"
        class="cfg-row"
      >
        <span class="cfg-row__no">{{ String(idx + 1).padStart(2, '0') }}</span>
        <span class="cfg-row__icon">
          <AppIcon :name="item.icon" :size="17" />
        </span>
        <span class="cfg-row__body">
          <span class="cfg-row__title">{{ item.title }}</span>
          <span class="cfg-row__desc">{{ item.desc() }}</span>
        </span>
        <AppIcon name="chevron-right" :size="16" class="cfg-row__arrow" />
      </RouterLink>
    </div>

    <section class="cfg-note mt-md">
      <h2 class="cfg-note__title">
        <AppIcon name="info" :size="14" />
        配置生效说明
      </h2>
      <ul class="config-note__list">
        <li>修改加班规则或取整规则后，<strong>仅对新提交的记录生效</strong>；历史记录按提交时固化的规则核算。</li>
        <li>单价修改后，新记录按新单价核算；已提交记录保留提交时的单价快照。</li>
        <li>工作类型被停用后，员工端不再可选；已有记录不受影响。</li>
        <li>员工被停用后无法登录，历史记录保留并照常参与历史统计。</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
/* 配置入口：编号 + 图标 + 文字，行式列表 */
.cfg-list {
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.cfg-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 14px var(--space-md);
  border-bottom: 1px solid var(--color-divider);
  color: inherit;
  text-decoration: none;
  transition: background-color var(--duration-fast) var(--ease-in-out);
}

.cfg-row:last-child {
  border-bottom: 0;
}

.cfg-row:hover {
  background: var(--color-surface-muted);
}

.cfg-row__no {
  font-family: var(--font-mono);
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  flex: 0 0 18px;
  font-variant-numeric: tabular-nums;
}

.cfg-row__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
  color: var(--color-accent);
}

.cfg-row__body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  flex: 1 1 auto;
  min-width: 0;
}

.cfg-row__title {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--color-fg);
}

.cfg-row__desc {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cfg-row__arrow {
  color: var(--color-fg-tertiary);
  flex: 0 0 auto;
}

/* 说明区块：左侧强调条 */
.cfg-note {
  position: relative;
  padding: var(--space-md) var(--space-md) var(--space-md) calc(var(--space-md) + 4px);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.cfg-note::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--color-accent);
}

.cfg-note__title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--color-fg);
}

.config-note__list {
  margin: var(--space-sm) 0 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: var(--text-sm);
  color: var(--color-fg-secondary);
  line-height: var(--leading-relaxed);
}

.config-note__list strong {
  color: var(--color-fg);
}
</style>
