<script setup lang="ts">
/**
 * 员工管理（CFG-01）
 * 增删改查、启用/停用、重置密码
 */
import { computed, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import AppSheet from '@/components/AppSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuth } from '@/stores/app'
import { useToast } from '@/stores/toast'
import {
  BizError,
  createEmployee,
  deleteEmployee,
  listEmployees,
  resetPassword,
  updateEmployee,
} from '@/data/service'
import { formatFullDateTime } from '@/core/date'
import type { Employee } from '@/core/types'

const { dataVersion, bumpDataVersion } = useAuth()
const toast = useToast()

const keyword = ref('')

const employees = computed(() => {
  void dataVersion.value
  const all = listEmployees().filter((e) => e.role === 'EMPLOYEE')
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return all
  return all.filter(
    (e) => e.name.toLowerCase().includes(kw) || e.account.toLowerCase().includes(kw),
  )
})

const activeCount = computed(() => employees.value.filter((e) => e.status === 'ACTIVE').length)

/* ---------------- 新增 / 编辑 ---------------- */
const formOpen = ref(false)
const editingId = ref<string | null>(null)
const form = reactive({ name: '', account: '', password: '' })
const formError = ref('')
const submitting = ref(false)

const isEditing = computed(() => !!editingId.value)

function openCreate(): void {
  editingId.value = null
  form.name = ''
  form.account = ''
  form.password = ''
  formError.value = ''
  formOpen.value = true
}

function openEdit(emp: Employee): void {
  editingId.value = emp.id
  form.name = emp.name
  form.account = emp.account
  form.password = ''
  formError.value = ''
  formOpen.value = true
}

async function submitForm(): Promise<void> {
  if (submitting.value) return
  formError.value = ''
  submitting.value = true
  try {
    if (isEditing.value) {
      updateEmployee(editingId.value!, { name: form.name, account: form.account })
      toast.success('员工信息已更新')
    } else {
      await createEmployee({
        name: form.name,
        account: form.account,
        password: form.password,
      })
      toast.success('员工已创建')
    }
    bumpDataVersion()
    formOpen.value = false
  } catch (err) {
    formError.value = err instanceof BizError ? err.message : '保存失败'
  } finally {
    submitting.value = false
  }
}

/* ---------------- 启用 / 停用 ---------------- */
function toggleStatus(emp: Employee): void {
  try {
    updateEmployee(emp.id, { status: emp.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })
    bumpDataVersion()
    toast.success(emp.status === 'ACTIVE' ? `已停用 ${emp.name}` : `已启用 ${emp.name}`)
  } catch (err) {
    toast.error(err instanceof BizError ? err.message : '操作失败')
  }
}

/* ---------------- 重置密码 ---------------- */
const pwdOpen = ref(false)
const pwdTarget = ref<Employee | null>(null)
const newPwd = ref('')
const pwdError = ref('')

function openResetPwd(emp: Employee): void {
  pwdTarget.value = emp
  newPwd.value = ''
  pwdError.value = ''
  pwdOpen.value = true
}

async function submitResetPwd(): Promise<void> {
  if (!pwdTarget.value) return
  pwdError.value = ''
  try {
    await resetPassword(pwdTarget.value.id, newPwd.value)
    toast.success(`已重置 ${pwdTarget.value.name} 的密码`)
    pwdOpen.value = false
  } catch (err) {
    pwdError.value = err instanceof BizError ? err.message : '重置失败'
  }
}

/* ---------------- 删除 ---------------- */
const deleteOpen = ref(false)
const deleteTarget = ref<Employee | null>(null)

function openDelete(emp: Employee): void {
  deleteTarget.value = emp
  deleteOpen.value = true
}

function doDelete(): void {
  if (!deleteTarget.value) return
  try {
    const result = deleteEmployee(deleteTarget.value.id)
    bumpDataVersion()
    toast.success(
      result.disabled
        ? `${deleteTarget.value.name} 已有服务记录，已改为停用以保留历史统计`
        : `${deleteTarget.value.name} 已删除`,
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
        <h1 class="page-head__title">员工管理</h1>
        <p class="page-head__desc">
          共 {{ employees.length }} 名工程师 · 在岗 {{ activeCount }} 人
        </p>
      </div>
      <div class="page-head__actions">
        <RouterLink to="/a/config" class="btn btn--ghost btn--sm">
          <AppIcon name="chevron-left" :size="15" />
          返回配置
        </RouterLink>
        <button type="button" class="btn btn--primary btn--sm" @click="openCreate">
          <AppIcon name="plus" :size="15" :stroke-width="2.4" />
          新增员工
        </button>
      </div>
    </div>

    <div class="filter-bar mb-md">
      <div class="filter-bar__search">
        <AppIcon name="search" :size="16" class="filter-bar__search-icon" />
        <input
          v-model="keyword"
          class="input"
          type="search"
          placeholder="搜索姓名或账号"
          aria-label="搜索员工"
        />
      </div>
    </div>

    <div v-if="employees.length === 0" class="card">
      <EmptyState
        icon="users"
        title="还没有员工"
        desc="新增员工后，员工即可使用账号密码登录并记录服务"
      >
        <button type="button" class="btn btn--primary btn--sm mt-sm" @click="openCreate">
          <AppIcon name="plus" :size="15" :stroke-width="2.4" />
          新增员工
        </button>
      </EmptyState>
    </div>

    <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>姓名</th>
            <th>登录账号</th>
            <th>状态</th>
            <th>创建时间</th>
            <th class="num">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="emp in employees" :key="emp.id">
            <td>
              <span class="emp-cell">
                <span class="emp-cell__avatar">{{ emp.name.slice(0, 1) }}</span>
                {{ emp.name }}
              </span>
            </td>
            <td class="mono">{{ emp.account }}</td>
            <td>
              <span class="dot-label" :class="emp.status === 'ACTIVE' ? 'dot-label--ok' : 'dot-label--off'">
                {{ emp.status === 'ACTIVE' ? '启用' : '停用' }}
              </span>
            </td>
            <td class="text-xs text-tertiary">{{ formatFullDateTime(emp.createdAt) }}</td>
            <td>
              <div class="row-actions">
                <button type="button" class="btn btn--ghost btn--sm" @click="openEdit(emp)">
                  编辑
                </button>
                <button type="button" class="btn btn--ghost btn--sm" @click="openResetPwd(emp)">
                  重置密码
                </button>
                <button type="button" class="btn btn--ghost btn--sm" @click="toggleStatus(emp)">
                  {{ emp.status === 'ACTIVE' ? '停用' : '启用' }}
                </button>
                <button
                  type="button"
                  class="btn btn--ghost btn--sm text-danger"
                  @click="openDelete(emp)"
                >
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
      :title="isEditing ? '编辑员工' : '新增员工'"
      @close="formOpen = false"
    >
      <form class="col gap-md" novalidate @submit.prevent="submitForm">
        <div v-if="formError" class="alert alert--danger">
          <AppIcon name="warning" :size="16" />
          <span>{{ formError }}</span>
        </div>

        <div class="field">
          <label class="field__label" for="emp-name">
            姓名
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <input
            id="emp-name"
            v-model="form.name"
            class="input"
            type="text"
            maxlength="20"
            placeholder="如：张三"
            autocomplete="off"
          />
        </div>

        <div class="field">
          <label class="field__label" for="emp-account">
            登录账号
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <input
            id="emp-account"
            v-model="form.account"
            class="input"
            type="text"
            maxlength="20"
            placeholder="3~20 位字母、数字或下划线"
            autocomplete="off"
            autocapitalize="off"
            spellcheck="false"
          />
        </div>

        <div v-if="!isEditing" class="field">
          <label class="field__label" for="emp-password">
            初始密码
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <input
            id="emp-password"
            v-model="form.password"
            class="input"
            type="text"
            placeholder="至少 6 位，员工首次登录后可自行修改"
            autocomplete="new-password"
          />
          <p class="field__hint">密码以不可逆摘要存储，创建后无法查看，仅可重置</p>
        </div>
      </form>

      <template #footer>
        <button type="button" class="btn btn--secondary" @click="formOpen = false">取消</button>
        <button type="button" class="btn btn--primary" :disabled="submitting" @click="submitForm">
          {{ submitting ? '保存中…' : '保存' }}
        </button>
      </template>
    </AppSheet>

    <!-- 重置密码 -->
    <AppSheet :open="pwdOpen" title="重置密码" @close="pwdOpen = false">
      <div class="col gap-md">
        <p class="text-sm text-secondary">
          即将重置 <strong>{{ pwdTarget?.name }}</strong>（{{ pwdTarget?.account }}）的登录密码。
        </p>

        <div v-if="pwdError" class="alert alert--danger">
          <AppIcon name="warning" :size="16" />
          <span>{{ pwdError }}</span>
        </div>

        <div class="field">
          <label class="field__label" for="reset-pwd">新密码</label>
          <input
            id="reset-pwd"
            v-model="newPwd"
            class="input"
            type="text"
            placeholder="至少 6 位"
            autocomplete="new-password"
          />
          <p class="field__hint">请将新密码线下告知员工，并提醒其登录后自行修改</p>
        </div>
      </div>

      <template #footer>
        <button type="button" class="btn btn--secondary" @click="pwdOpen = false">取消</button>
        <button type="button" class="btn btn--primary" @click="submitResetPwd">确认重置</button>
      </template>
    </AppSheet>

    <!-- 删除确认 -->
    <AppSheet :open="deleteOpen" title="删除员工" @close="deleteOpen = false">
      <p class="text-sm text-secondary">
        确定要删除 <strong>{{ deleteTarget?.name }}</strong> 吗？
      </p>
      <p class="text-sm text-tertiary mt-sm">
        若该员工已有服务记录，系统将改为「停用」以保留历史统计，不会真正删除数据。
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

.emp-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.emp-cell__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-surface-sunken);
  color: var(--color-fg-secondary);
  font-size: 11px;
  font-weight: 700;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  justify-content: flex-end;
  flex-wrap: wrap;
}
</style>
