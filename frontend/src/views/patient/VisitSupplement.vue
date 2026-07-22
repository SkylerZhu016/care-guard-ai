<template>
  <div class="page" v-loading="loading">
    <div class="page-card" v-if="visit">
      <h3 class="page-title">补充问诊信息（{{ visit.visitNo }}）</h3>
      <el-alert type="warning" title="医务人员要求您补充以下信息，修改后将重新进入审核队列" show-icon :closable="false" class="mb-12" />
      <el-form :model="form" label-width="110px" style="max-width: 720px">
        <el-form-item label="主诉"><el-input v-model="form.chiefComplaint" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="起病时间">
          <el-date-picker v-model="form.onsetTime" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="持续时间"><el-input v-model="form.duration" /></el-form-item>
        <el-form-item label="严重程度">
          <el-radio-group v-model="form.severity">
            <el-radio v-for="(v, k) in severityMap" :key="k" :value="k">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="伴随症状">
          <el-select v-model="form.accompanying" multiple filterable allow-create class="w-100">
            <el-option v-for="s in commonSymptoms" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="补充说明"><el-input v-model="form.supplement" type="textarea" :rows="3" /></el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">重新提交</el-button>
          <el-button @click="$router.back()">返回</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { visitApi } from '@/api/modules'
import type { Visit, VisitForm } from '@/types'
import { commonSymptoms, severityMap } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const visitId = Number(route.params.id)
const loading = ref(true)
const submitting = ref(false)
const visit = ref<Visit | null>(null)
const form = reactive<VisitForm>({ basic: { specialGroup: 'NONE' }, chiefComplaint: '', accompanying: [] })

onMounted(async () => {
  try {
    const { data } = await visitApi.detail(visitId)
    visit.value = data
    Object.assign(form, data.formData)
  } finally { loading.value = false }
})

async function submit() {
  submitting.value = true
  try {
    await visitApi.supplement(visitId, form)
    ElMessage.success('已补充并重新提交')
    router.push(`/p/visits/${visitId}`)
  } finally { submitting.value = false }
}
</script>
