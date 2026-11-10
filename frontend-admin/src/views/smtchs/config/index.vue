<template>
  <div class="app-container">
    <el-alert
      title="修改后仅对新提交的记录生效。已提交记录按提交时固化的规则核算，保证历史口径不变。"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <el-card shadow="never" style="max-width: 640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px" v-loading="loading">
        <el-form-item label="加班起算时间" prop="overtimeStart">
          <el-time-picker
            v-model="overtimeStartTime"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="选择时间"
            style="width: 180px"
            @change="handleTimeChange"
          />
          <span style="margin-left: 12px; color: #909399; font-size: 12px">
            默认 18:00，即 18 点后按加班倍率计算
          </span>
        </el-form-item>

        <el-form-item label="加班倍率" prop="overtimeMultiplier">
          <el-input-number
            v-model="form.overtimeMultiplier"
            :min="1"
            :max="10"
            :step="0.1"
            :precision="2"
            style="width: 180px"
          />
          <span style="margin-left: 12px; color: #909399; font-size: 12px">
            加班时段金额 = 加班时长 × 单价 × 倍率
          </span>
        </el-form-item>

        <el-form-item label="时长取整规则" prop="roundingRule">
          <el-radio-group v-model="form.roundingRule">
            <el-radio label="EXACT">精确到分钟</el-radio>
            <el-radio label="HALF_HOUR_UP">半小时向上取整</el-radio>
            <el-radio label="HOUR_UP">整小时向上取整</el-radio>
          </el-radio-group>
          <div style="color: #909399; font-size: 12px; line-height: 1.6; margin-top: 4px">
            正常段与加班段<strong>分别</strong>按此规则向上补足
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="submitForm" v-hasPermi="['smtchs:config:edit']">保存配置</el-button>
          <el-button @click="loadConfig">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="max-width: 640px; margin-top: 16px">
      <template #header>
        <span style="font-weight: 600">核算口径说明</span>
      </template>
      <ul style="margin: 0; padding-left: 18px; line-height: 2; font-size: 13px; color: #606266">
        <li><strong>按次</strong>记录不参与加班与取整，一条记录 = 1 次。</li>
        <li><strong>按小时</strong>记录以加班起算时间为界切分：正常段 T₁、加班段 T₂ 分别取整后计费。</li>
        <li>开始时间已晚于起算点时，全部时长计入加班段。</li>
        <li>结束时间早于起算点时，全部时长计入正常段。</li>
        <li>跨 0 点的连续服务时长归入加班段连续计算。</li>
      </ul>
    </el-card>
  </div>
</template>

<script setup name="SmtConfig">
import { getConfig, updateConfig } from '@/api/smtchs/config'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const form = ref({
  overtimeStart: '18:00',
  overtimeMultiplier: 2,
  roundingRule: 'EXACT'
})
const overtimeStartTime = ref('18:00')

const rules = {
  overtimeStart: [{ required: true, message: '请选择加班起算时间', trigger: 'change' }],
  overtimeMultiplier: [{ required: true, message: '请输入加班倍率', trigger: 'blur' }],
  roundingRule: [{ required: true, message: '请选择取整规则', trigger: 'change' }]
}

function loadConfig() {
  loading.value = true
  getConfig().then(res => {
    const data = res.data || {}
    form.value = {
      overtimeStart: data.overtimeStart || '18:00',
      overtimeMultiplier: Number(data.overtimeMultiplier) || 2,
      roundingRule: data.roundingRule || 'EXACT'
    }
    overtimeStartTime.value = form.value.overtimeStart
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

function handleTimeChange(val) {
  form.value.overtimeStart = val
}

function submitForm() {
  proxy.$refs['formRef'].validate(valid => {
    if (!valid) return
    updateConfig(form.value).then(() => {
      proxy.$modal.msgSuccess('配置已保存，对新提交的记录生效')
      loadConfig()
    }).catch(() => {
      proxy.$modal.msgError('保存失败')
    })
  })
}

loadConfig()
</script>
