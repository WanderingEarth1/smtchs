<script setup lang="ts">
/**
 * 工作类型管理（CFG-02）
 * 增删改查、启用/停用、设定计费模式
 */
import { computed, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import AppSheet from '@/components/AppSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuth } from '@/stores/app'
import { useToast } from '@/stores/toast'
import {
  BizError,
  createWorkType,
  deleteWorkType,
  listWorkTypes,
  updateWorkType,
} from '@/data/service'
import type { BillingMode, WorkType } from '@/core/types'

const { dataVersion, bumpDataVersion } = useAuth()
const toast = useToast()

const workTypes = computed(() => {
  void dataVersion.value
  return listWorkTypes()
})

const enabledCount = computed(() => workTypes.value.filter((w) => w.enabled).length)

/* ---------------- 新增 / 编辑 ---------------- */
const formOpen = ref(false)
const editingId = ref<string | null>(null)
const form = reactive<{ name: string; billingMode: BillingMode }>({
  name: '',
  billingMode: 'BY_COUNT',
})
const formError = ref('')

const isEditing = computed(() => !!editingId.value)

function openCreate(): void {
  editingId.value = null
  form.name = ''
  form.billingMode = 'BY_COUNT'
  formError.value = ''
  formOpen.value = true
}

function openEdit(wt: WorkType): void {
  editingId.value = wt.id
  form.name = wt.name
  form.billingMode = wt.billingMode
  formError.value = ''
  formOpen.value = true
}

function submitForm(): void {
  formError.value = ''
  try {
    if (isEditing.value) {
      updateWorkType(editingId.value!, { name: form.name, billingMode: form.billingMode })
      toast.success('工作类型已更新')
    } else {
      createWorkType({ name: form.name, billingMode: form.billingMode })
      toast.success('工作类型已创建')
    }
    bumpDataVersion()
    formOpen.value = false
  } catch (err) {
    formError.value = err instanceof BizError ? err.message : '保存失败'
  }
}

/* ---------------- 启用 / 停用 ---------------- */
function toggleEnabled(wt: WorkType): void {
  try {
    updateWorkType(wt.id, { enabled: !wt.enabled })
    bumpDataVersion()
    toast.success(wt.enabled ? `已停用「${wt.name}」` : `已启用「${wt.name}」`)
  } catch (err) {
    toast.error(err instanceof BizError ? err.message : '操作失败')
  }
}

/* ---------------- 删除 ---------------- */
const deleteOpen = ref(false)
const deleteTarget = ref<WorkType | null>(null)

function openDelete(wt: WorkType): void {
  deleteTarget.value = wt
  deleteOpen.value = true
}

function doDelete(): void {
  if (!deleteTarget.value) return
  try {
    const result = deleteWorkType(deleteTarget.value.id)
    bumpDataVersion()
    toast.success(
      result.disabled
        ? `「${deleteTarget.value.name}」已被记录引用，已改为停用`
        : `「${deleteTarget.value.name}」已删除`,
    )
    deleteOpen.value = false
  } catch (err) {
    toast.error(err instanceof BizError ? err.message : '删除失败')
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-head__title">工作类型</h1>
        <p class="page-head__desc">
          共 {{ workTypes.length }} 个类型 · 启用 {{ enabledCount }} 个 · 计费模式决定核算方式
        </p>
      </div>
      <div class="page-head__actions">
        <RouterLink to="/a/config" class="btn btn--ghost btn--sm">
          <AppIcon name="chevron-left" :size="15" />
          返回配置
        </RouterLink>
        <button type="button" class="btn btn--primary btn--sm" @click="openCreate">
          <AppIcon name="plus" :size="15" :stroke-width="2.4" />
          新增类型
        </button>
      </div>
    </div>

    <div class="alert alert--info mb-md">
      <AppIcon name="info" :size="16" />
      <span>
        <strong>按次</strong>：一条记录 = 1 次，不参与加班与取整；
        <strong>按小时</strong>：按服务时长计费，18 点后按加班倍率计算。
      </span>
    </div>

    <div v-if="workTypes.length === 0" class="card">
      <EmptyState icon="tag" title="还没有工作类型" desc="新增工作类型后，员工才能选择并记录服务">
        <button type="button" class="btn btn--primary btn--sm mt-sm" @click="openCreate">
          <AppIcon name="plus" :size="15" :stroke-width="2.4" />
          新增类型
        </button>
      </EmptyState>
    </div>

    <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>类型名称</th>
            <th>计费模式</th>
            <th>状态</th>
            <th class="num">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="wt in workTypes" :key="wt.id">
            <td class="fw-600">{{ wt.name }}</td>
            <td>
              <span class="tag" :class="wt.billingMode === 'BY_HOUR' ? 'tag--hour' : 'tag--count'">
                {{ wt.billingMode === 'BY_HOUR' ? '按小时' : '按次' }}
              </span>
            </td>
            <td>
              <span class="dot-label" :class="wt.enabled ? 'dot-label--ok' : 'dot-label--off'">
                {{ wt.enabled ? '启用' : '停用' }}
              </span>
            </td>
            <td>
              <div class="row-actions">
                <button type="button" class="btn btn--ghost btn--sm" @click="openEdit(wt)">编辑</button>
                <button type="button" class="btn btn--ghost btn--sm" @click="toggleEnabled(wt)">
                  {{ wt.enabled ? '停用' : '启用' }}
                </button>
                <button type="button" class="btn btn--ghost btn--sm text-danger" @click="openDelete(wt)">
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 新增 / 编辑 -->
    <AppSheet
      :open="formOpen"
      :title="isEditing ? '编辑工作类型' : '新增工作类型'"
      @close="formOpen = false"
    >
      <div class="col gap-md">
        <div v-if="formError" class="alert alert--danger">
          <AppIcon name="warning" :size="16" />
          <span>{{ formError }}</span>
        </div>

        <div class="field">
          <label class="field__label" for="wt-name">
            类型名称
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <input
            id="wt-name"
            v-model="form.name"
            class="input"
            type="text"
            maxlength="20"
            placeholder="如：送货上门、安装调试、维修维护"
            autocomplete="off"
          />
        </div>

        <div class="field">
          <span class="field__label">
            计费模式
            <span class="field__required" aria-hidden="true">*</span>
          </span>
          <div class="mode-options" role="radiogroup" aria-label="选择计费模式">
            <button
              type="button"
              role="radio"
              class="mode-option"
              :aria-checked="form.billingMode === 'BY_COUNT'"
              :class="{ 'mode-option--active': form.billingMode === 'BY_COUNT' }"
              @click="form.billingMode = 'BY_COUNT'"
            >
              <span class="mode-option__title">按次计费</span>
              <span class="mode-option__desc">一条记录 = 1 次，单价 × 次数，无加班</span>
            </button>
            <button
              type="button"
              role="radio"
              class="mode-option"
              :aria-checked="form.billingMode === 'BY_HOUR'"
              :class="{ 'mode-option--active': form.billingMode === 'BY_HOUR' }"
              @click="form.billingMode = 'BY_HOUR'"
            >
              <span class="mode-option__title">按小时计费</span>
              <span class="mode-option__desc">按服务时长计费，18 点后按加班倍率</span>
            </button>
          </div>
          <p v-if="isEditing" class="field__hint">
            修改计费模式仅影响新提交的记录，历史记录按提交时的规则核算
          </p>
        </div>
      </div>

      <template #footer>
        <button type="button" class="btn btn--secondary" @click="formOpen = false">取消</button>
        <button type="button" class="btn btn--primary" @click="submitForm">保存</button>
      </template>
    </AppSheet>

    <!-- 删除确认 -->
    <AppSheet :open="deleteOpen" title="删除工作类型" @close="deleteOpen = false">
      <p class="text-sm text-secondary">
        确定要删除 <strong>{{ deleteTarget?.name }}</strong> 吗？
      </p>
      <p class="text-sm text-tertiary mt-sm">
        若该类型已被服务记录引用，系统将改为「停用」以保留历史数据。
      </p>
      <template #footer>
        <button type="button" class="btn btn--secondary" @click="deleteOpen = false">取消</button>
        <button type="button" class="btn btn--danger" @click="doDelete">确认删除</button>
      </template>
    </AppSheet>
  </div>
</template>

<style scoped>
.mb-md {
  margin-bottom: var(--space-md);
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.mode-options {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.mode-option {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 10px var(--space-md);
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  text-align: left;
  transition: all var(--duration-fast) var(--ease-in-out);
  color: var(--color-fg);
}

.mode-option:hover {
  border-color: var(--color-border-strong);
  background: var(--color-surface-muted);
}

.mode-option--active {
  border-color: var(--color-accent);
  background: var(--color-info-bg);
}

.mode-option__title {
  font-size: var(--text-sm);
  font-weight: 600;
}

.mode-option__desc {
  font-size: var(--text-xs);
  color: var(--color-fg-tertiary);
}
</style>
