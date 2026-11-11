<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="月份" prop="month">
        <el-date-picker v-model="queryParams.month" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width: 160px" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="待确认" value="PENDING" />
          <el-option label="已确认" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
      </el-form-item>
      <el-form-item label="员工" prop="employeeId">
        <el-select v-model="queryParams.employeeId" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="e in employees" :key="e.id" :label="e.name" :value="e.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="工作类型" prop="workTypeId">
        <el-select v-model="queryParams.workTypeId" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="w in workTypes" :key="w.id" :label="w.name" :value="w.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词" prop="keyword">
        <el-input v-model="queryParams.keyword" placeholder="地点/内容/流水号" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" style="margin-bottom: 12px"></right-toolbar>

    <el-table v-loading="loading" :data="list" height="calc(100vh - 400px)" @row-click="handleView">
      <el-table-column label="流水号" prop="serialNo" min-width="150" show-overflow-tooltip />
      <el-table-column label="员工" prop="employeeName" min-width="100" show-overflow-tooltip />
      <el-table-column label="日期" prop="serviceDate" min-width="110" />
      <el-table-column label="工作类型" prop="workTypeName" min-width="110" show-overflow-tooltip />
      <el-table-column label="计费模式" width="110" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.snapBillingMode === 'BY_HOUR' ? 'warning' : 'success'" size="small">
            {{ scope.row.snapBillingMode === 'BY_HOUR' ? '按小时' : '按次' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" min-width="130">
        <template #default="scope">
          {{ scope.row.startTime }}<span v-if="scope.row.endTime">~{{ scope.row.endTime }}</span>
        </template>
      </el-table-column>
      <el-table-column label="地点" prop="location" show-overflow-tooltip min-width="140" />
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
      <el-table-column label="操作" width="100" fixed="right" align="center">
        <template #default="scope">
          <el-button link type="primary" @click.stop="handleView(scope.row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

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
        <el-descriptions-item label="备注">{{ detail.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detail.status)">{{ statusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="驳回原因" v-if="detail.rejectReason">
          <span style="color: #f56c6c">{{ detail.rejectReason }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detail.submittedAt }}</el-descriptions-item>
        <el-descriptions-item label="单价">{{ detail.snapUnitPrice }} 元</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup name="SmtRecord">
import { listRecord } from '@/api/smtchs/record'
import { listEmployee } from '@/api/smtchs/employee'
import { listWorkType } from '@/api/smtchs/worktype'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const showSearch = ref(true)
const list = ref([])
const total = ref(0)
const employees = ref([])
const workTypes = ref([])
const detailOpen = ref(false)
const detail = ref(null)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  month: undefined,
  status: undefined,
  employeeId: undefined,
  workTypeId: undefined,
  keyword: undefined
})

function statusText(s) {
  return { PENDING: '待确认', APPROVED: '已确认', REJECTED: '已驳回' }[s] || s
}

function statusType(s) {
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] || 'info'
}

function getList() {
  loading.value = true
  listRecord(queryParams.value).then(res => {
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
  listWorkType().then(res => {
    workTypes.value = res.data || res.rows || []
  }).catch(() => {})
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleView(row) {
  detail.value = row
  detailOpen.value = true
}

loadOptions()
getList()
</script>
