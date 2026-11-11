<template>
  <div class="app-container">
    <el-alert
      title="单价修改后仅对新提交的记录生效；已提交记录保留提交时的单价快照，保证历史核算口径不变。"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <el-table v-loading="loading" :data="matrix" border>
      <el-table-column label="工程师" prop="employeeName" width="140" fixed="left" />
      <el-table-column
        v-for="wt in workTypes"
        :key="wt.id"
        :label="wt.name + '（' + (wt.billingMode === 'BY_HOUR' ? '元/小时' : '元/次') + '）'"
        min-width="150"
        align="center"
      >
        <template #default="scope">
          <el-input-number
            v-model="scope.row.prices[wt.id]"
            :min="0"
            :precision="2"
            :step="10"
            size="small"
            controls-position="right"
            style="width: 120px"
            @change="handlePriceChange(scope.row, wt)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right" align="center">
        <template #default="scope">
          <el-button link type="primary" icon="CopyDocument" @click="handleCopy(scope.row)" v-hasPermi="['smtchs:price:edit']">复制此行</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="复制单价配置" v-model="copyOpen" width="480px" append-to-body>
      <p style="margin-bottom: 12px">
        将 <strong>{{ copyFromName }}</strong> 的全部单价配置复制到以下员工（已存在的会被覆盖）：
      </p>
      <el-checkbox-group v-model="copyTargets">
        <el-checkbox
          v-for="emp in copyCandidates"
          :key="emp.id"
          :label="emp.id"
          style="display: block; margin-bottom: 8px"
        >
          {{ emp.name }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitCopy" :disabled="copyTargets.length === 0">
            复制到 {{ copyTargets.length }} 人
          </el-button>
          <el-button @click="copyOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SmtPrice">
import { getPriceMatrix, setPrice, copyPriceRow } from '@/api/smtchs/price'
import { listEmployee } from '@/api/smtchs/employee'
import { listWorkType } from '@/api/smtchs/worktype'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const matrix = ref([])
const workTypes = ref([])
const employees = ref([])

const copyOpen = ref(false)
const copyFromId = ref(null)
const copyFromName = ref('')
const copyTargets = ref([])

const copyCandidates = computed(() =>
  employees.value.filter(e => e.id !== copyFromId.value)
)

/** 加载矩阵数据 */
async function loadData() {
  loading.value = true
  try {
    const [rulesRes, typesRes, empsRes] = await Promise.all([
      getPriceMatrix(),
      listWorkType(),
      listEmployee({ pageNum: 1, pageSize: 999 })
    ])

    const rules = rulesRes.data || rulesRes.rows || []
    workTypes.value = (typesRes.data || typesRes.rows || []).filter(w => w.enabled)
    // 只保留启用中的工程师（排除管理员账号）
    employees.value = (empsRes.rows || empsRes.data || [])
      .filter(e => e.status === '0' && e.role === 'EMPLOYEE')

    // 组装矩阵：行=员工，列=工作类型单价
    matrix.value = employees.value.map(emp => {
      const prices = {}
      workTypes.value.forEach(wt => {
        const rule = rules.find(r => r.employeeId === emp.id && r.workTypeId === wt.id)
        prices[wt.id] = rule ? Number(rule.price) : 0
      })
      return { employeeId: emp.id, employeeName: emp.name, prices }
    })
  } catch (e) {
    proxy.$modal.msgError('加载单价矩阵失败')
  } finally {
    loading.value = false
  }
}

/** 单元格修改后保存 */
function handlePriceChange(row, wt) {
  const price = row.prices[wt.id]
  setPrice({ employeeId: row.employeeId, workTypeId: wt.id, price }).then(() => {
    proxy.$modal.msgSuccess('单价已保存')
  }).catch(() => {
    proxy.$modal.msgError('保存失败')
    loadData()
  })
}

function handleCopy(row) {
  copyFromId.value = row.employeeId
  copyFromName.value = row.employeeName
  copyTargets.value = []
  copyOpen.value = true
}

function submitCopy() {
  copyPriceRow({
    fromEmployeeId: copyFromId.value,
    toEmployeeIds: copyTargets.value
  }).then(res => {
    const count = res.data || 0
    proxy.$modal.msgSuccess('已复制 ' + count + ' 条单价配置')
    copyOpen.value = false
    loadData()
  }).catch(() => {
    proxy.$modal.msgError('复制失败')
  })
}

loadData()
</script>
