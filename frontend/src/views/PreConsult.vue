<template>
  <div class="pre-consult">
    <el-card class="page-header">
      <h2><el-icon style="vertical-align:middle"><Edit /></el-icon> 智能预问诊</h2>
      <p class="page-desc">教学模拟系统 · 请填写症状信息进行预问诊评估（不涉及真实诊断）</p>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="16">
        <el-card class="form-card">
          <template #header><span>症状信息填报</span></template>
          <el-form :model="form" label-width="100px" label-position="top">
            <el-form-item label="患者" required>
              <el-select v-model="form.patientId" placeholder="选择模拟患者" style="width:100%">
                <el-option v-for="p in patients" :key="p.id" :label="`${p.name} (${p.age}岁 ${p.gender})`" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="主诉" required>
              <el-input v-model="form.chiefComplaint" type="textarea" :rows="3" placeholder="请描述主要不适症状，如：头痛3天，伴有发热" />
            </el-form-item>
            <el-form-item label="症状详情">
              <div v-for="(s, idx) in form.symptoms" :key="idx" class="symptom-item">
                <el-row :gutter="8">
                  <el-col :span="6"><el-input v-model="s.symptomName" placeholder="症状名称" /></el-col>
                  <el-col :span="4"><el-input v-model="s.bodyPart" placeholder="部位" /></el-col>
                  <el-col :span="3">
                    <el-select v-model="s.severity" placeholder="程度">
                      <el-option v-for="i in 10" :key="i" :label="i" :value="i" />
                    </el-select>
                  </el-col>
                  <el-col :span="4"><el-input v-model="s.duration" placeholder="持续时间" /></el-col>
                  <el-col :span="5"><el-input v-model="s.description" placeholder="详细描述" /></el-col>
                  <el-col :span="2">
                    <el-button type="danger" :icon="Delete" circle @click="removeSymptom(idx)" />
                  </el-col>
                </el-row>
              </div>
              <el-button type="primary" size="small" :icon="Plus" @click="addSymptom">添加症状</el-button>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="large" @click="submitConsult" :loading="submitting" style="width:100%">
                {{ submitting ? 'AI 分析中...' : '提交预问诊' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- Triage Result Panel -->
      <el-col :span="8">
        <el-card class="result-card">
          <template #header><span><el-icon><DataBoard /></el-icon> 分诊结果</span></template>
          <div v-if="triageResult" class="result-content">
            <div class="risk-badge" :class="triageResult.riskLevel">
              <span class="risk-dot"></span>
              {{ riskLabel(triageResult.riskLevel) }}
            </div>
            <div class="risk-score">风险评分: <strong>{{ triageResult.riskScore }}</strong></div>
            <el-divider />
            <div class="section">
              <h4>红旗症状</h4>
              <p>{{ triageResult.redFlags || '未发现红旗症状' }}</p>
            </div>
            <div class="section">
              <h4>风险因素</h4>
              <p>{{ triageResult.riskFactors || '无' }}</p>
            </div>
            <div class="section">
              <h4>建议</h4>
              <p>{{ triageResult.recommendations || '请等待医生审核' }}</p>
            </div>
            <el-tag v-if="triageResult.safetyChecked" type="success">已通过安全审核</el-tag>
            <el-tag v-else type="warning">待安全审核</el-tag>
          </div>
          <el-empty v-else description="提交预问诊后将显示分诊结果" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { consultApi } from '@/api'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import type { SimulatedPatient, TriageResult } from '@/types'

const patients = ref<SimulatedPatient[]>([])
const triageResult = ref<TriageResult | null>(null)
const submitting = ref(false)

const form = reactive({
  patientId: null as number | null,
  chiefComplaint: '',
  symptoms: [{ symptomName: '', bodyPart: '', severity: 5, duration: '', description: '' }],
})

function addSymptom() {
  form.symptoms.push({ symptomName: '', bodyPart: '', severity: 5, duration: '', description: '' })
}

function removeSymptom(idx: number) {
  form.symptoms.splice(idx, 1)
}

async function submitConsult() {
  if (!form.patientId || !form.chiefComplaint) {
    ElMessage.warning('请选择患者并填写主诉')
    return
  }
  submitting.value = true
  try {
    const res = await consultApi.submit({ ...form })
    if (res.code === 200) {
      ElMessage.success('提交成功，正在进行AI分诊分析...')
      // Simulate triage result
      setTimeout(async () => {
        try {
          const triageRes = await consultApi.getTriage(res.data.id)
          if (triageRes.code === 200) triageResult.value = triageRes.data
        } catch {}
      }, 1500)
    }
  } finally {
    submitting.value = false
  }
}

function riskLabel(level: string) {
  const map: Record<string, string> = { LOW: '低风险', MEDIUM: '中等风险', HIGH: '高风险', CRITICAL: '危急' }
  return map[level] || level
}

onMounted(async () => {
  try {
    const res = await http.get('/patients/list')
    if (res.code === 200) patients.value = res.data
  } catch {}
})
</script>

<style scoped>
.pre-consult { max-width: 1400px; margin: 0 auto; }
.page-header { border-radius: 12px; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
.page-desc { color: #909399; font-size: 13px; margin-top: 6px; }
.form-card, .result-card { border-radius: 12px; }
.symptom-item { margin-bottom: 12px; padding: 12px; background: #f8f9fb; border-radius: 8px; }
.result-content { display: flex; flex-direction: column; gap: 12px; }
.risk-badge { display: flex; align-items: center; gap: 8px; padding: 10px 16px; border-radius: 8px; font-weight: 600; font-size: 16px; }
.risk-badge.CRITICAL { background: #fef0f0; color: #f56c6c; }
.risk-badge.HIGH { background: #fdf6ec; color: #e6a23c; }
.risk-badge.MEDIUM { background: #f0f9eb; color: #67c23a; }
.risk-badge.LOW { background: #ecf5ff; color: #409eff; }
.risk-dot { width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
.risk-score { font-size: 15px; color: #606266; }
.section h4 { font-size: 13px; color: #909399; margin-bottom: 4px; }
.section p { font-size: 14px; color: #303133; line-height: 1.6; }
</style>
