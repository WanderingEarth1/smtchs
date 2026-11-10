<script setup lang="ts">
/**
 * 单价矩阵（CFG-03）
 * 按「工程师 × 工作类型」逐项设定单价，支持按行快速复制
 */
import { computed, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import AppSheet from '@/components/AppSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuth } from '@/stores/app'
import { useToast } from '@/stores/toast'
import {
  BizError,
  copyPriceRow,
  getUnitPrice,
  listEngineers,
  listWorkTypes,
  setUnitPrice,
} from '@/data/service'
import { formatMoney } from '@/core/date'

const { dataVersion, bumpDataVersion } = useAuth()
const toast = useToast()

const engineers = computed(() => {
  void dataVersion.value
  return listEngineers().filter((e) => e.status === 'ACTIVE')
})

const workTypes = computed(() => {
  void dataVersion.value
  return listWorkTypes(false)
})

/** 编辑单元格 */
const editOpen = ref(false)
const editTarget = reactive({
  employeeId: '',
  employeeName: '',
  workTypeId: '',
  workTypeName: '',
  billingMode: 'BY_COUNT' as 'BY_COUNT' | 'BY_HOUR',
  price: '',
})
const editError = ref('')

function priceOf(employeeId: string, workTypeId: string): number {
  void dataVersion.value
  return getUnitPrice(employeeId, workTypeId)
}

function openEdit(employeeId: string, employeeName: string, workTypeId: string): void {
  const wt = workTypes.value.find((w) => w.id === workTypeId)
  if (!wt) return
  editTarget.employeeId = employeeId
  editTarget.employeeName = employeeName
  editTarget.workTypeId = workTypeId
  editTarget.workTypeName = wt.name
  editTarget.billingMode = wt.billingMode
  const current = priceOf(employeeId, workTypeId)
  editTarget.price = current > 0 ? String(current) : ''
  editError.value = ''
  editOpen.value = true
}

function submitEdit(): void {
  editError.value = ''
  const price = Number(editTarget.price)
  if (!editTarget.price.trim()) {
    editError.value = '请输入单价'
    return
  }
  if (!Number.isFinite(price) || price < 0) {
    editError.value = '单价必须为非负数'
    return
  }
  try {
    setUnitPrice(editTarget.employeeId, editTarget.workTypeId, price)
    bumpDataVersion()
    toast.success(
      `已将 ${editTarget.employeeName} 的「${editTarget.workTypeName}」单价设为 ¥${formatMoney(price)}`,
    )
    editOpen.value = false
  } catch (err) {
    editError.value = err instanceof BizError ? err.message : '保存失败'
  }
}

/* ---------------- 按行复制 ---------------- */
const copyOpen = ref(false)
const copyFrom = ref<string>('')
const copyTo = ref<Set<string>>(new Set())

function openCopy(employeeId: string): void {
  copyFrom.value = employeeId
  copyTo.value = new Set()
  copyOpen.value = true
}

function toggleCopyTarget(id: string): void {
  const next = new Set(copyTo.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  copyTo.value = next
}

function submitCopy(): void {
  if (copyTo.value.size === 0) {
    toast.error('请至少选择一名目标员工')
    return
  }
  try {
    const count = copyPriceRow(copyFrom.value, [...copyTo.value])
    bumpDataVersion()
    toast.success(`已复制 ${count} 条单价配置`)
    copyOpen.value = false
  } catch (err) {
    toast.error(err instanceof BizError ? err.message : '复制失败')
  }
}

const copyFromName = computed(
  () => engineers.value.find((e) => e.id === copyFrom.value)?.name ?? '',
)

/** 某员工已配置的单价数量 */
function configuredCount(employeeId: string): number {
  void dataVersion.value
  return workTypes.value.filter((wt) => getUnitPrice(employeeId, wt.id) > 0).length
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-head__title">单价矩阵</h1>
        <p class="page-head__desc">
          按「工程师 × 工作类型」逐项设定单价 · 点击单元格即可编辑
        </p>
      </div>
      <div class="page-head__actions">
        <RouterLink to="/a/config" class="btn btn--ghost btn--sm">
          <AppIcon name="chevron-left" :size="15" />
          返回配置
        </RouterLink>
      </div>
    </div>

    <div class="alert alert--info mb-md">
      <AppIcon name="info" :size="16" />
      <span>
        单价修改后仅对<strong>新提交</strong>的记录生效；已提交记录保留提交时的单价快照，保证历史核算口径不变。
      </span>
    </div>

    <div v-if="engineers.length === 0" class="card">
      <EmptyState icon="users" title="还没有在岗员工" desc="请先在「员工管理」中新增员工" />
    </div>

    <div v-else class="table-wrap">
      <table class="table price-table">
        <thead>
          <tr>
            <th class="price-table__sticky">工程师</th>
            <th v-for="wt in workTypes" :key="wt.id" class="num">
              <div class="price-head">
                <span>{{ wt.name }}</span>
                <span class="price-head__mode">
                  {{ wt.billingMode === 'BY_HOUR' ? '元/小时' : '元/次' }}
                </span>
              </div>
            </th>
            <th class="num">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="emp in engineers" :key="emp.id">
            <td class="price-table__sticky">
              <span class="price-emp">
                <span class="price-emp__avatar">{{ emp.name.slice(0, 1) }}</span>
                <span>
                  <span class="price-emp__name">{{ emp.name }}</span>
                  <span class="price-emp__meta">
                    {{ configuredCount(emp.id) }}/{{ workTypes.length }} 已配置
                  </span>
                </span>
              </span>
            </td>
            <td v-for="wt in workTypes" :key="wt.id" class="num">
              <button
                type="button"
                class="price-cell"
                :class="{ 'price-cell--empty': priceOf(emp.id, wt.id) <= 0 }"
                :aria-label="`编辑 ${emp.name} 的 ${wt.name} 单价`"
                @click="openEdit(emp.id, emp.name, wt.id)"
              >
                <template v-if="priceOf(emp.id, wt.id) > 0">
                  ¥{{ formatMoney(priceOf(emp.id, wt.id)) }}
                </template>
                <template v-else>未设置</template>
              </button>
            </td>
            <td class="num">
              <button type="button" class="btn btn--ghost btn--sm" @click="openCopy(emp.id)">
                <AppIcon name="copy" :size="14" />
                复制此行
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 编辑单价 -->
    <AppSheet :open="editOpen" title="设置单价" @close="editOpen = false">
      <div class="col gap-md">
        <div class="price-edit-head">
          <span class="fw-600">{{ editTarget.employeeName }}</span>
          <span class="text-tertiary">·</span>
          <span>{{ editTarget.workTypeName }}</span>
          <span class="tag" :class="editTarget.billingMode === 'BY_HOUR' ? 'tag--hour' : 'tag--count'">
            {{ editTarget.billingMode === 'BY_HOUR' ? '按小时' : '按次' }}
          </span>
        </div>

        <div v-if="editError" class="alert alert--danger">
          <AppIcon name="warning" :size="16" />
          <span>{{ editError }}</span>
        </div>

        <div class="field">
          <label class="field__label" for="price-input">
            单价（{{ editTarget.billingMode === 'BY_HOUR' ? '元/小时' : '元/次' }}）
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <input
            id="price-input"
            v-model="editTarget.price"
            class="input tabular"
            type="number"
            inputmode="decimal"
            min="0"
            step="0.01"
            placeholder="如：80"
          />
          <p class="field__hint">设为 0 或留空表示该员工不承接此类型工作</p>
        </div>
      </div>

      <template #footer>
        <button type="button" class="btn btn--secondary" @click="editOpen = false">取消</button>
        <button type="button" class="btn btn--primary" @click="submitEdit">保存</button>
      </template>
    </AppSheet>

    <!-- 复制行 -->
    <AppSheet :open="copyOpen" title="复制单价配置" @close="copyOpen = false">
      <div class="col gap-md">
        <p class="text-sm text-secondary">
          将 <strong>{{ copyFromName }}</strong> 的全部单价配置复制到以下员工（已存在的会被覆盖）：
        </p>

        <div class="copy-list">
          <label v-for="emp in engineers.filter((e) => e.id !== copyFrom)" :key="emp.id" class="copy-item">
            <button
              type="button"
              class="checkbox"
              role="checkbox"
              :aria-checked="copyTo.has(emp.id)"
              :aria-label="`选择 ${emp.name}`"
              @click="toggleCopyTarget(emp.id)"
            >
              <AppIcon v-if="copyTo.has(emp.id)" name="check" :size="14" :stroke-width="3" />
            </button>
            <span class="grow">{{ emp.name }}</span>
            <span class="text-xs text-tertiary">{{ configuredCount(emp.id) }} 项已配置</span>
          </label>
        </div>
      </div>

      <template #footer>
        <button type="button" class="btn btn--secondary" @click="copyOpen = false">取消</button>
        <button type="button" class="btn btn--primary" @click="submitCopy">
          复制到 {{ copyTo.size }} 人
        </button>
      </template>
    </AppSheet>
  </div>
</template>

<style scoped>
.mb-md {
  margin-bottom: var(--space-md);
}

.price-table {
  min-width: 860px;
}

.price-table__sticky {
  position: sticky;
  left: 0;
  z-index: 2;
  background: var(--color-surface);
  box-shadow: 1px 0 0 var(--color-border);
}

.price-table thead .price-table__sticky {
  background: var(--color-surface-muted);
  z-index: 3;
}

.price-head {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0;
  line-height: 1.3;
}

.price-head__mode {
  font-size: 10px;
  font-weight: 400;
  color: var(--color-fg-tertiary);
}

.price-emp {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.price-emp__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: 50%;
  background: var(--color-surface-sunken);
  color: var(--color-fg-secondary);
  font-size: 11px;
  font-weight: 700;
}

.price-emp__name {
  display: block;
  font-weight: 500;
  font-size: var(--text-sm);
}

.price-emp__meta {
  display: block;
  font-size: 10px;
  color: var(--color-fg-tertiary);
}

.price-cell {
  min-width: 76px;
  min-height: 32px;
  padding: 4px 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-fg);
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.price-cell:hover {
  border-color: var(--color-accent);
  background: var(--color-surface-muted);
}

.price-cell--empty {
  color: var(--color-fg-tertiary);
  font-style: italic;
  font-size: var(--text-xs);
}

.price-edit-head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: var(--space-sm) var(--space-md);
  background: var(--color-surface-muted);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
}

.copy-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 320px;
  overflow-y: auto;
}

.copy-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 8px;
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  cursor: pointer;
}

.copy-item:hover {
  background: var(--color-surface-muted);
}
</style>
