<template>
  <div class="page">
    <div class="page-card">
      <div class="flex-between mb-12">
        <h3 class="page-title" style="margin:0">新建预问诊</h3>
        <div>
          <el-button :loading="savingDraft" @click="saveDraft()">保存草稿</el-button>
          <el-button v-if="step === steps.length - 1" type="primary" :loading="submitting" @click="submit">提交问诊</el-button>
        </div>
      </div>
      <el-steps :active="step" align-center finish-status="success" class="mb-12">
        <el-step v-for="(s, i) in steps" :key="i" :title="s" @click="step = i" style="cursor:pointer" />
      </el-steps>

      <el-form ref="formRef" :model="form" label-width="110px" style="max-width: 760px">
        <!-- 0 选择患者 + 基本情况 -->
        <template v-if="step === 0">
          <el-form-item label="选择患者" required>
            <el-select v-model="patientId" placeholder="请选择模拟患者" class="w-100">
              <el-option v-for="p in patients" :key="p.id" :label="`${p.name}（${p.patientNo}）`" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="身高 (cm)"><el-input-number v-model="form.basic.height" :min="30" :max="250" /></el-form-item>
          <el-form-item label="体重 (kg)"><el-input-number v-model="form.basic.weight" :min="2" :max="300" /></el-form-item>
          <el-form-item label="特殊人群">
            <el-radio-group v-model="form.basic.specialGroup">
              <el-radio v-for="(v, k) in specialGroupMap" :key="k" :value="k">{{ v }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>

        <!-- 1 主诉 -->
        <template v-if="step === 1">
          <el-form-item label="主诉" required>
            <el-input v-model="form.chiefComplaint" type="textarea" :rows="4" maxlength="500" show-word-limit
              placeholder="请描述最主要的不适，例如：胸痛伴呼吸困难 2 小时" />
          </el-form-item>
        </template>

        <!-- 2 起病情况 -->
        <template v-if="step === 2">
          <el-form-item label="起病时间" required>
            <el-date-picker v-model="form.onsetTime" type="date" value-format="YYYY-MM-DD" placeholder="症状开始日期" />
          </el-form-item>
          <el-form-item label="持续时间">
            <el-input v-model="form.duration" placeholder="例如：3 天 / 2 小时" />
          </el-form-item>
          <el-form-item label="严重程度">
            <el-radio-group v-model="form.severity">
              <el-radio v-for="(v, k) in severityMap" :key="k" :value="k">{{ v }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>

        <!-- 3 伴随症状 -->
        <template v-if="step === 3">
          <el-form-item label="伴随症状">
            <el-select v-model="form.accompanying" multiple filterable allow-create default-first-option
              placeholder="选择或输入伴随症状" class="w-100">
              <el-option v-for="s in commonSymptoms" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
        </template>

        <!-- 4 诱因与变化 -->
        <template v-if="step === 4">
          <el-form-item label="可能诱因"><el-input v-model="form.triggers" type="textarea" :rows="2" placeholder="例如：受凉、劳累、饮食不当" /></el-form-item>
          <el-form-item label="缓解因素"><el-input v-model="form.reliefFactors" type="textarea" :rows="2" placeholder="什么情况会好转" /></el-form-item>
          <el-form-item label="加重因素"><el-input v-model="form.aggravatingFactors" type="textarea" :rows="2" placeholder="什么情况会加重" /></el-form-item>
        </template>

        <!-- 5 既往史 / 6 过敏史 / 7 用药 -->
        <template v-if="step === 5">
          <el-form-item label="既往病史"><el-input v-model="form.pastHistory" type="textarea" :rows="3" placeholder="例如：高血压 5 年" /></el-form-item>
        </template>
        <template v-if="step === 6">
          <el-form-item label="药物/食物过敏"><el-input v-model="form.allergyHistory" type="textarea" :rows="3" placeholder="例如：青霉素过敏；无则填「无」" /></el-form-item>
        </template>
        <template v-if="step === 7">
          <el-form-item label="当前用药"><el-input v-model="form.medication" type="textarea" :rows="3" placeholder="例如：氨氯地平 5mg 每日一次；无则填「无」" /></el-form-item>
        </template>

        <!-- 8 补充 + 确认 -->
        <template v-if="step === 8">
          <el-form-item label="补充说明"><el-input v-model="form.supplement" type="textarea" :rows="3" placeholder="其他想补充的信息（可选）" /></el-form-item>
          <el-alert type="warning" :title="DISCLAIMER" show-icon :closable="false" />
          <el-descriptions :column="1" border class="mt-12" title="提交前确认">
            <el-descriptions-item label="主诉">{{ form.chiefComplaint || '（未填写）' }}</el-descriptions-item>
            <el-descriptions-item label="起病时间">{{ form.onsetTime || '（未填写）' }}</el-descriptions-item>
            <el-descriptions-item label="严重程度">{{ form.severity ? severityMap[form.severity] : '-' }}</el-descriptions-item>
            <el-descriptions-item label="伴随症状">{{ form.accompanying.join('、') || '无' }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </el-form>

      <div class="mt-12">
        <el-button :disabled="step === 0" @click="step--">上一步</el-button>
        <el-button v-if="step < steps.length - 1" type="primary" @click="next">下一步</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { patientApi, visitApi } from '@/api/modules'
import type { Patient, VisitForm } from '@/types'
import { severityMap, specialGroupMap, commonSymptoms, DISCLAIMER } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const steps = ['基本情况', '主诉', '起病情况', '伴随症状', '诱因变化', '既往史', '过敏史', '用药情况', '补充确认']
const step = ref(0)
const patients = ref<Patient[]>([])
const patientId = ref<number>()
const savingDraft = ref(false)
const submitting = ref(false)
const draftId = ref<number>()

const form = reactive<VisitForm>({
  basic: { height: null, weight: null, specialGroup: 'NONE' },
  chiefComplaint: '',
  accompanying: []
})

function next() {
  if (step.value === 0 && !patientId.value) return ElMessage.warning('请先选择患者')
  if (step.value === 1 && !form.chiefComplaint.trim()) return ElMessage.warning('请填写主诉')
  if (step.value === 2 && !form.onsetTime) return ElMessage.warning('请选择起病时间')
  step.value++
}

async function saveDraft(silent = false) {
  if (!patientId.value) { if (!silent) ElMessage.warning('请先选择患者'); return }
  savingDraft.value = true
  try {
    const { data } = await visitApi.saveDraft({ id: draftId.value, patientId: patientId.value, formData: form })
    draftId.value = data.id
    if (!silent) ElMessage.success('草稿已保存')
  } finally { savingDraft.value = false }
}

async function submit() {
  if (!patientId.value || !form.chiefComplaint.trim() || !form.onsetTime) {
    return ElMessage.warning('请完成必填项（患者/主诉/起病时间）')
  }
  submitting.value = true
  try {
    const { data } = await visitApi.submit({
      patientId: patientId.value,
      formData: form,
      idempotencyKey: crypto.randomUUID()
    })
    ElMessage.success('已提交，系统正在处理')
    router.push(`/p/visits/${data.id}`)
  } finally { submitting.value = false }
}

onMounted(async () => {
  const { data } = await patientApi.page({ page: 0, size: 50 })
  patients.value = data.content
  // 从草稿继续
  const draft = Number(route.query.draft)
  if (draft) {
    const resp = await visitApi.detail(draft)
    draftId.value = resp.data.id
    patientId.value = resp.data.patientId
    Object.assign(form, resp.data.formData)
  }
})
</script>
