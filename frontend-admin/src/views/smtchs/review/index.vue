<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="待确认" name="PENDING" />
      <el-tab-pane label="已确认" name="APPROVED" />
      <el-tab-pane label="已驳回" name="REJECTED" />
      <el-tab-pane label="全部" name="ALL" />
    </el-tabs>

    <el-form :model="queryParams" ref="queryRef" :inline="true">
      <el-form-item label="月份" prop="month">
        <el-date-picker v-model="queryParams.month" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width: 160px" />
      </el-form-item>
      <el-form-item label="员工" prop="employeeId">
        <el-select v-model="queryParams.employeeId" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="e in employees" :key="e.id" :label="e.name" :value="e.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8" v-if="activeTab === 'PENDING'">
      <el-col :span="1.5">
        <el-button type="success" plain icon="Check" :disabled="selectedIds.length === 0" @click="handleBatchApprove" v-hasPermi="['smtchs:review:approve']">
          批量确认（{{ selectedIds.length }}）
        </el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="list" height="calc(100vh - 420px)" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" :selectable="row => row.status === 'PENDING'" />
      <el-table-column label="员工" prop="employeeName" min-width="100" show-overflow-tooltip />
      <el-table-column label="工作类型" prop="workTypeName" min-width="120" show-overflow-tooltip />
      <el-table-column label="日期" prop="serviceDate" min-width="120" />
      <el-table-column label="时间" min-width="150">
        <template #default="scope">
          {{ scope.row.startTime }}<span v-if="scope.row.endTime">~{{ scope.row.endTime }}</span>
        </template>
      </el-table-column>
      <el-table-column label="地点" prop="location" show-overflow-tooltip min-width="150" />
      <el-table-column label="工作内容" prop="content" show-overflow-tooltip min-width="160" />
      <el-table-column label="状态" width="110" align="center">
        <template #default="scope">
          <el-tag :type="statusType(scope.row.status)" size="small">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="补录" width="80" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.isBackfill" type="info" size="small">补录</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right" align="center">
        <template #default="scope">
          <template v-if="scope.row.status === 'PENDING'">
            <el-button link type="success" class="op-btn" @click="handleApprove(scope.row)" v-hasPermi="['smtchs:review:approve']">确认</el-button>
            <el-button link type="danger" class="op-btn" @click="handleReject(scope.row)" v-hasPermi="['smtchs:review:reject']">驳回</el-button>
          </template>
          <el-button v-if="scope.row.status === 'APPROVED'" link type="primary" @click="handleRevise(scope.row)" v-hasPermi="['smtchs:review:revise']">修正</el-button>
          <el-button link type="primary" class="op-btn" @click="handleDetail(scope.row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 驳回弹窗 -->
    <el-dialog title="驳回记录" v-model="rejectOpen" width="480px" append-to-body>
      <el-alert title="驳回后员工可修改并重新提交，将重走审核流程" type="warning" :closable="false" show-icon style="margin-bottom: 16px" />
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="90px">
        <el-form-item label="驳回原因" prop="reason">
          <el-input v-model="rejectForm.reason" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="请说明驳回原因，员工可见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="danger" @click="submitReject">确认驳回</el-button>
          <el-button @click="rejectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 修正弹窗 -->
    <el-dialog title="修正已确认记录" v-model="reviseOpen" width="560px" append-to-body>
      <el-alert title="修正将保留「修正人、修正时间、修正前原值」痕迹，记录按新值参与核算" type="warning" :closable="false" show-icon style="margin-bottom: 16px" />
      <el-form ref="reviseFormRef" :model="reviseForm" :rules="reviseRules" label-width="100px">
        <el-form-item label="服务日期" prop="serviceDate">
          <el-date-picker v-model="reviseForm.serviceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-time-picker v-model="reviseForm.startTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-time-picker v-model="reviseForm.endTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item label="地点" prop="location">
          <el-input v-model="reviseForm.location" maxlength="60" />
        </el-form-item>
        <el-form-item label="工作内容" prop="content">
          <el-input v-model="reviseForm.content" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
        <el-form-item label="修正说明" prop="note">
          <el-input v-model="reviseForm.note" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="如：电话核实实际结束时间为 20:00" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitRevise">保存修正</el-button>
          <el-button @click="reviseOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailOpen" title="记录详情" size="480px">
      <el-descriptions :column="1" border v-if="detail">
        <el-descriptions-item label="流水号">{{ detail.serialNo }}</el-descriptions-item>
        <el-descriptions-item label="员工">{{ detail.employeeName }}</el-descriptions-item>
        <el-descriptions-item label="工作类型">{{ detail.workTypeName }}</el-descriptions-item>
        <el-descriptions-item label="服务日期">{{ detail.serviceDate }}</el-descriptions-item>
        <el-descriptions-item label="时间">
          {{ detail.startTime }}<span v-if="detail.endTime"> ~ {{ detail.endTime }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="地点">{{ detail.location }}</el-descriptions-item>
        <el-descriptions-item label="工作内容">{{ detail.content }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detail.status)">{{ statusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="驳回原因" v-if="detail.rejectReason">
          <span style="color: #f56c6c">{{ detail.rejectReason }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup name="SmtReview">
import { listRecord } from '@/api/smtchs/record'
import { listEmployee } from '@/api/smtchs/employee'
import { approveRecord, approveBatch, rejectRecord, reviseRecord } from '@/api/smtchs/review'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const activeTab = ref('PENDING')
const list = ref([])
const total = ref(0)
const employees = ref([])
const selectedIds = ref([])
const detailOpen = ref(false)
const detail = ref(null)

const rejectOpen = ref(false)
const reviseOpen = ref(false)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  month: undefined,
  employeeId: undefined
})

const rejectForm = ref({ id: null, reason: '' })
const reviseForm = ref({ id: null, serviceDate: '', startTime: '', endTime: '', location: '', content: '', note: '' })

const rejectRules = {
  reason: [{ required: true, message: '驳回原因不能为空', trigger: 'blur' }]
}

const reviseRules = {
  note: [{ required: true, message: '修正说明不能为空', trigger: 'blur' }]
}

function statusText(s) {
  return { PENDING: '待确认', APPROVED: '已确认', REJECTED: '已驳回' }[s] || s
}

function statusType(s) {
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] || 'info'
}

function getList() {
  loading.value = true
  const params = { ...queryParams.value }
  if (activeTab.value !== 'ALL') {
    params.status = activeTab.value
  }
  listRecord(params).then(res => {
    list.value = res.rows || res.data || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

function loadOptions() {
  listEmployee({ pageNum: 1, pageSize: 999 }).then(res => {
    employees.value = (res.rows || res.data || []).filter(e => e.role === 'EMPLOYEE')
  }).catch(() => {})
}

function handleTabChange() {
  queryParams.value.pageNum = 1
  selectedIds.value = []
  getList()
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection) {
  selectedIds.value = selection.map(item => item.id)
}

function handleApprove(row) {
  proxy.$modal.confirm('确认该记录？确认后记录锁定并计入提成核算。').then(() => {
    return approveRecord(row.id)
  }).then(() => {
    proxy.$modal.msgSuccess('已确认')
    getList()
  }).catch(() => {})
}

function handleBatchApprove() {
  if (selectedIds.value.length === 0) return
  proxy.$modal.confirm('即将确认 ' + selectedIds.value.length + ' 条记录，确认后锁定并计入核算。').then(() => {
    return approveBatch(selectedIds.value)
  }).then(res => {
    const data = res.data || {}
    proxy.$modal.msgSuccess('已确认 ' + (data.success || 0) + ' 条，跳过 ' + (data.failed || 0) + ' 条')
    selectedIds.value = []
    getList()
  }).catch(() => {})
}

function handleReject(row) {
  rejectForm.value = { id: row.id, reason: '' }
  rejectOpen.value = true
}

function submitReject() {
  proxy.$refs['rejectFormRef'].validate(valid => {
    if (!valid) return
    rejectRecord(rejectForm.value.id, rejectForm.value.reason).then(() => {
      proxy.$modal.msgSuccess('已驳回')
      rejectOpen.value = false
      getList()
    })
  })
}

function handleRevise(row) {
  reviseForm.value = {
    id: row.id,
    serviceDate: row.serviceDate,
    startTime: row.startTime,
    endTime: row.endTime || '',
    location: row.location,
    content: row.content,
    note: ''
  }
  reviseOpen.value = true
}

function submitRevise() {
  proxy.$refs['reviseFormRef'].validate(valid => {
    if (!valid) return
    const { id, ...data } = reviseForm.value
    reviseRecord(id, data).then(() => {
      proxy.$modal.msgSuccess('修正已保存，已记录修正痕迹')
      reviseOpen.value = false
      getList()
    })
  })
}

function handleDetail(row) {
  detail.value = row
  detailOpen.value = true
}

loadOptions()
getList()
</script>
