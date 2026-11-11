<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="姓名" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入姓名" clearable style="width: 160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="账号" prop="account">
        <el-input v-model="queryParams.account" placeholder="请输入账号" clearable style="width: 160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['smtchs:employee:add']">新增</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" height="calc(100vh - 340px)">
      <el-table-column label="姓名" prop="name" min-width="140" show-overflow-tooltip />
      <el-table-column label="登录账号" prop="account" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="110" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'info'">
            {{ scope.row.status === '0' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" min-width="180" />
      <el-table-column label="操作" width="260" fixed="right" align="center">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" class="op-btn" @click="handleUpdate(scope.row)" v-hasPermi="['smtchs:employee:edit']">修改</el-button>
          <el-button link type="primary" icon="Key" class="op-btn" @click="handleResetPwd(scope.row)" v-hasPermi="['smtchs:employee:resetPwd']">重置密码</el-button>
          <el-button link type="danger" icon="Delete" class="op-btn" @click="handleDelete(scope.row)" v-hasPermi="['smtchs:employee:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/修改 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" maxlength="20" />
        </el-form-item>
        <el-form-item label="登录账号" prop="account">
          <el-input v-model="form.account" placeholder="3~20 位字母、数字或下划线" maxlength="20" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码" prop="password">
          <el-input v-model="form.password" placeholder="至少 6 位" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog title="重置密码" v-model="pwdOpen" width="420px" append-to-body>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="pwdForm.password" placeholder="至少 6 位" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitPwd">确 定</el-button>
          <el-button @click="pwdOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SmtEmployee">
import { listEmployee, addEmployee, updateEmployee, delEmployee, resetEmployeePwd } from '@/api/smtchs/employee'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const showSearch = ref(true)
const list = ref([])
const total = ref(0)
const title = ref('')
const open = ref(false)
const pwdOpen = ref(false)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  name: undefined,
  account: undefined,
  status: undefined
})

const form = ref({})
const pwdForm = ref({ id: null, password: '' })

const rules = {
  name: [{ required: true, message: '姓名不能为空', trigger: 'blur' }],
  account: [
    { required: true, message: '登录账号不能为空', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '3~20 位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '初始密码不能为空', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

const pwdRules = {
  password: [
    { required: true, message: '新密码不能为空', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

function getList() {
  loading.value = true
  listEmployee(queryParams.value).then(res => {
    list.value = res.rows || res.data || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleAdd() {
  form.value = { name: '', account: '', password: '' }
  open.value = true
  title.value = '新增员工'
}

function handleUpdate(row) {
  form.value = { ...row, password: undefined }
  open.value = true
  title.value = '修改员工'
}

function submitForm() {
  proxy.$refs['formRef'].validate(valid => {
    if (!valid) return
    const action = form.value.id ? updateEmployee : addEmployee
    action(form.value).then(() => {
      proxy.$modal.msgSuccess(form.value.id ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleResetPwd(row) {
  pwdForm.value = { id: row.id, password: '' }
  pwdOpen.value = true
}

function submitPwd() {
  proxy.$refs['pwdFormRef'].validate(valid => {
    if (!valid) return
    resetEmployeePwd(pwdForm.value.id, pwdForm.value.password).then(() => {
      proxy.$modal.msgSuccess('密码重置成功')
      pwdOpen.value = false
    })
  })
}

function handleDelete(row) {
  proxy.$modal.confirm('确认删除员工「' + row.name + '」？若有历史记录将改为停用。').then(() => {
    return delEmployee(row.id)
  }).then(res => {
    const disabled = res && res.data && res.data.disabled
    proxy.$modal.msgSuccess(disabled ? '该员工有历史记录，已改为停用' : '删除成功')
    getList()
  }).catch(() => {})
}

function cancel() {
  open.value = false
}

getList()
</script>
