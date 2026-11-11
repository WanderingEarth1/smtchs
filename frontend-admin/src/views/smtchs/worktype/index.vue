<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="类型名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入类型名称" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['smtchs:worktype:add']">新增</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" height="calc(100vh - 340px)">
      <el-table-column label="类型名称" prop="name" min-width="200" show-overflow-tooltip />
      <el-table-column label="计费模式" width="140" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.billingMode === 'BY_HOUR' ? 'warning' : 'success'">
            {{ scope.row.billingMode === 'BY_HOUR' ? '按小时' : '按次' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.enabled ? 'success' : 'info'">
            {{ scope.row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sort" width="90" align="center" />
      <el-table-column label="操作" width="180" fixed="right" align="center">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" class="op-btn" @click="handleUpdate(scope.row)" v-hasPermi="['smtchs:worktype:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" class="op-btn" @click="handleDelete(scope.row)" v-hasPermi="['smtchs:worktype:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="类型名称" prop="name">
          <el-input v-model="form.name" placeholder="如：送货上门、安装调试" maxlength="20" />
        </el-form-item>
        <el-form-item label="计费模式" prop="billingMode">
          <el-radio-group v-model="form.billingMode">
            <el-radio label="BY_COUNT">按次计费</el-radio>
            <el-radio label="BY_HOUR">按小时计费</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SmtWorkType">
import { listWorkType, addWorkType, updateWorkType, delWorkType } from '@/api/smtchs/worktype'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const showSearch = ref(true)
const list = ref([])
const title = ref('')
const open = ref(false)

const queryParams = ref({ name: undefined })
const form = ref({})

const rules = {
  name: [{ required: true, message: '类型名称不能为空', trigger: 'blur' }],
  billingMode: [{ required: true, message: '请选择计费模式', trigger: 'change' }]
}

function getList() {
  loading.value = true
  listWorkType(queryParams.value).then(res => {
    list.value = res.rows || res.data || []
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

function handleQuery() {
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  getList()
}

function handleAdd() {
  form.value = { name: '', billingMode: 'BY_COUNT', enabled: true }
  open.value = true
  title.value = '新增工作类型'
}

function handleUpdate(row) {
  form.value = { ...row }
  open.value = true
  title.value = '修改工作类型'
}

function submitForm() {
  proxy.$refs['formRef'].validate(valid => {
    if (!valid) return
    const action = form.value.id ? updateWorkType : addWorkType
    action(form.value).then(() => {
      proxy.$modal.msgSuccess(form.value.id ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  proxy.$modal.confirm('确认删除类型「' + row.name + '」？若已被记录引用将改为停用。').then(() => {
    return delWorkType(row.id)
  }).then(res => {
    const disabled = res && res.data && res.data.disabled
    proxy.$modal.msgSuccess(disabled ? '该类型已被引用，已改为停用' : '删除成功')
    getList()
  }).catch(() => {})
}

getList()
</script>
