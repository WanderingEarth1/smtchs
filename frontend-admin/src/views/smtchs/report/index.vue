<template>
  <div class="app-container">
    <el-form :inline="true">
      <el-form-item label="月份">
        <el-date-picker v-model="month" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width: 160px" @change="loadReport" />
      </el-form-item>
      <el-form-item label="员工">
        <el-select v-model="employeeId" placeholder="全部" clearable style="width: 140px" @change="loadReport">
          <el-option v-for="e in employees" :key="e.id" :label="e.name" :value="e.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="工作类型">
        <el-select v-model="workTypeId" placeholder="全部" clearable style="width: 140px" @change="loadReport">
          <el-option v-for="w in workTypes" :key="w.id" :label="w.name" :value="w.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Download" :loading="exporting" :disabled="!report || report.approvedRecords.length === 0" @click="handleExport" v-hasPermi="['smtchs:report:export']">
          导出 Excel
        </el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="report && (report.pendingCount > 0 || report.rejectedCount > 0)"
      :title="'本月另有 ' + report.pendingCount + ' 条待确认、' + report.rejectedCount + ' 条已驳回记录未计入核算，请及时处理以免漏算'"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <!-- 汇总卡片：对齐 H5 的指标卡风格 -->
    <div class="smt-metrics" v-loading="loading">
      <div class="smt-metrics__lead">
        <span class="smt-metrics__lead-label">提成总额</span>
        <p class="smt-metrics__lead-value">
          <em>¥</em>{{ formatMoney(report?.summary?.totalAmount) }}
        </p>
        <span class="smt-metrics__lead-foot">
          {{ report?.approvedRecords?.length || 0 }} 条已确认计入
        </span>
      </div>
      <div class="smt-metrics__grid">
        <div class="smt-metrics__cell">
          <span class="smt-metrics__cell-k">按次次数</span>
          <span class="smt-metrics__cell-v">{{ report?.summary?.countTotal || 0 }}<em>次</em></span>
          <span class="smt-metrics__cell-f">¥{{ formatMoney(report?.summary?.byCountAmount) }}</span>
        </div>
        <div class="smt-metrics__cell">
          <span class="smt-metrics__cell-k">正常时长</span>
          <span class="smt-metrics__cell-v">{{ formatHours(report?.summary?.normalHoursTotal) }}</span>
          <span class="smt-metrics__cell-f">按时计费</span>
        </div>
        <div class="smt-metrics__cell">
          <span class="smt-metrics__cell-k">加班时长</span>
          <span class="smt-metrics__cell-v is-warn">{{ formatHours(report?.summary?.overtimeHoursTotal) }}</span>
          <span class="smt-metrics__cell-f">已按倍率</span>
        </div>
      </div>
    </div>

    <!-- 按员工汇总 -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>
        <span style="font-weight: 600">按员工汇总</span>
        <span style="float: right; color: #909399; font-size: 12px">按提成金额排序 · 点击行下钻明细</span>
      </template>
      <el-table :data="report?.byEmployee || []" @row-click="handleDrill">
        <el-table-column label="员工" prop="employeeName" min-width="140" show-overflow-tooltip />
        <el-table-column label="次数" min-width="100" align="right">
          <template #default="scope">{{ scope.row.summary.countTotal }}</template>
        </el-table-column>
        <el-table-column label="正常时长" min-width="130" align="right">
          <template #default="scope">{{ formatHours(scope.row.summary.normalHoursTotal) }}</template>
        </el-table-column>
        <el-table-column label="加班时长" min-width="130" align="right">
          <template #default="scope">{{ formatHours(scope.row.summary.overtimeHoursTotal) }}</template>
        </el-table-column>
        <el-table-column label="按次金额" min-width="140" align="right">
          <template #default="scope">¥{{ formatMoney(scope.row.summary.byCountAmount) }}</template>
        </el-table-column>
        <el-table-column label="按时金额" min-width="140" align="right">
          <template #default="scope">¥{{ formatMoney(scope.row.summary.byHourAmount) }}</template>
        </el-table-column>
        <el-table-column label="提成合计" min-width="150" align="right">
          <template #default="scope">
            <strong>¥{{ formatMoney(scope.row.summary.totalAmount) }}</strong>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 按类型汇总 -->
    <el-card shadow="never">
      <template #header>
        <span style="font-weight: 600">按工作类型汇总</span>
      </template>
      <el-row :gutter="16">
        <el-col :span="8" v-for="item in report?.byWorkType || []" :key="item.workTypeId" style="margin-bottom: 12px">
          <el-card shadow="hover">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
              <span style="font-weight: 600">{{ item.workTypeName }}</span>
              <el-tag :type="item.billingMode === 'BY_HOUR' ? 'warning' : 'success'" size="small">
                {{ item.billingMode === 'BY_HOUR' ? '按小时' : '按次' }}
              </el-tag>
            </div>
            <div class="stat-value" style="font-size: 20px">¥{{ formatMoney(item.summary.totalAmount) }}</div>
            <div class="stat-hint">
              {{ item.summary.recordCount }} 条 · 正常 {{ formatHours(item.summary.normalHoursTotal) }} · 加班 {{ formatHours(item.summary.overtimeHoursTotal) }}
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 下钻抽屉 -->
    <el-drawer v-model="drillOpen" :title="drillName + ' · 核算明细'" size="520px">
      <el-table :data="drillRecords" size="small">
        <el-table-column label="日期" prop="serviceDate" width="110" />
        <el-table-column label="工作类型" prop="workTypeName" width="110" />
        <el-table-column label="时间" width="130">
          <template #default="scope">
            {{ scope.row.startTime }}<span v-if="scope.row.endTime">~{{ scope.row.endTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="scope">¥{{ formatMoney(scope.row.snapUnitPrice) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup name="SmtReport">
import { getMonthlyReport, exportReport } from '@/api/smtchs/report'
import { listEmployee } from '@/api/smtchs/employee'
import { listWorkType } from '@/api/smtchs/worktype'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const exporting = ref(false)
const report = ref(null)
const employees = ref([])
const workTypes = ref([])

const now = new Date()
const month = ref(now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0'))
const employeeId = ref(undefined)
const workTypeId = ref(undefined)

const drillOpen = ref(false)
const drillName = ref('')
const drillRecords = ref([])

function formatMoney(v) {
  const n = Number(v) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatHours(v) {
  const h = Number(v) || 0
  if (h === 0) return '0h'
  const totalMin = Math.round(h * 60)
  const hh = Math.floor(totalMin / 60)
  const mm = totalMin % 60
  if (hh === 0) return mm + 'min'
  if (mm === 0) return hh + 'h'
  return hh + 'h' + mm + 'm'
}

function loadReport() {
  if (!month.value) return
  loading.value = true
  getMonthlyReport({
    month: month.value,
    employeeId: employeeId.value,
    workTypeId: workTypeId.value
  }).then(res => {
    report.value = res.data || null
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

function handleDrill(row) {
  drillName.value = row.employeeName
  drillRecords.value = (report.value?.approvedRecords || []).filter(r => r.employeeId === row.employeeId)
  drillOpen.value = true
}

function handleExport() {
  exporting.value = true
  exportReport(month.value).then(blob => {
    const url = window.URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.href = url
    link.download = '月度报表_' + month.value + '.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    proxy.$modal.msgSuccess('导出成功')
  }).catch(() => {
    proxy.$modal.msgError('导出失败')
  }).finally(() => {
    exporting.value = false
  })
}

loadOptions()
loadReport()
</script>

<style scoped>
/* ---------- 指标区（对齐 H5） ---------- */
.smt-metrics {
  display: grid;
  grid-template-columns: 1fr;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--smt-radius-lg);
  overflow: hidden;
  margin-bottom: 16px;
  box-shadow: var(--smt-shadow-xs);
}

@media (min-width: 900px) {
  .smt-metrics {
    grid-template-columns: minmax(260px, 0.85fr) 2.4fr;
  }
}

.smt-metrics__lead {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 18px 22px;
  background:
    radial-gradient(120% 100% at 0% 0%, rgba(56, 189, 248, 0.18), transparent 60%),
    linear-gradient(160deg, #0b1220 0%, #16294c 100%);
  color: #fff;
}

.smt-metrics__lead-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}

.smt-metrics__lead-value {
  margin: 4px 0 0;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.smt-metrics__lead-value em {
  font-style: normal;
  font-size: 16px;
  font-weight: 500;
  opacity: 0.6;
  margin-right: 2px;
}

.smt-metrics__lead-foot {
  margin-top: 2px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.55);
}

.smt-metrics__grid {
  display: grid;
  grid-template-columns: repeat(1, minmax(0, 1fr));
}

@media (min-width: 700px) {
  .smt-metrics__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.smt-metrics__cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 16px 20px;
  border-left: 1px solid var(--el-border-color-lighter);
  border-top: 1px solid var(--el-border-color-lighter);
}

.smt-metrics__cell-k {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--el-text-color-secondary);
}

.smt-metrics__cell-v {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.smt-metrics__cell-v.is-warn {
  color: var(--el-color-warning);
}

.smt-metrics__cell-v em {
  font-style: normal;
  font-size: 12px;
  font-weight: 500;
  margin-left: 2px;
  opacity: 0.6;
}

.smt-metrics__cell-f {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

</style>
