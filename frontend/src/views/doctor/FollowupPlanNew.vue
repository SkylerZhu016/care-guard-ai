<template>
  <div class="page" v-loading="loading">
    <div class="page-card">
      <h3 class="page-title">创建随访计划</h3>
      <el-form :model="form" label-width="110px" style="max-width: 720px">
        <el-form-item label="关联问诊单">
          <el-input :model-value="visit?.visitNo" disabled />
        </el-form-item>
        <el-form-item label="计划名称" required>
          <el-input v-model="form.planName" placeholder="例如：胸痛风险随访计划" />
        </el-form-item>
        <el-form-item label="随访频率">
          每 <el-input-number v-model="form.intervalDays" :min="1" :max="30" style="margin: 0 8px" /> 天一次
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="随访次数">
          <el-input-number v-model="taskCount" :min="1" :max="12" />
        </el-form-item>
        <el-form-item label="结束条件">
          <el-input v-model="form.endCondition" placeholder="例如：完成 4 次随访且症状稳定" />
        </el-form-item>
        <el-form-item label="随访内容项">
          <div v-for="(item, i) in form.items" :key="i" class="item-row">
            <el-input v-model="item.title" placeholder="项目，如：症状复查" style="width: 180px" />
            <el-input v-model="item.content" placeholder="内容说明" style="flex:1" />
            <el-button type="danger" plain @click="form.items.splice(i, 1)">删除</el-button>
          </div>
          <el-button size="small" @click="form.items.push({ title: '', content: '' })">+ 添加内容项</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save(true)">创建并启动</el-button>
          <el-button :loading="saving" @click="save(false)">仅保存</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { followupApi, visitApi } from '@/api/modules'
import type { Visit } from '@/types'

const route = useRoute()
const router = useRouter()
const visitId = Number(route.params.visitId)
const loading = ref(true)
const saving = ref(false)
const visit = ref<Visit | null>(null)
const taskCount = ref(4)

const form = reactive({
  planName: '',
  intervalDays: 3,
  startDate: new Date().toISOString().slice(0, 10),
  endCondition: '',
  items: [{ title: '症状复查', content: '询问当前症状变化与不适' }] as { title: string; content: string }[]
})

onMounted(async () => {
  try {
    const { data } = await visitApi.detail(visitId)
    visit.value = data
    form.planName = `${data.patientName || '患者'}随访计划`
  } finally { loading.value = false }
})

async function save(start: boolean) {
  if (!form.planName.trim() || !visit.value) return ElMessage.warning('请填写计划名称')
  saving.value = true
  try {
    const { data: plan } = await followupApi.createPlan({
      visitId,
      patientId: visit.value.patientId,
      planName: form.planName,
      intervalDays: form.intervalDays,
      startDate: form.startDate,
      endCondition: form.endCondition,
      items: form.items,
      // 任务生成数量透传给后端（生成 taskCount 个任务）
      ...( { taskCount: taskCount.value } as object)
    })
    if (start) await followupApi.planAction(plan.id, 'start')
    ElMessage.success(start ? '计划已创建并启动' : '计划已保存')
    router.push('/d/workbench')
  } finally { saving.value = false }
}
</script>

<style scoped>
.item-row { display: flex; gap: 8px; margin-bottom: 8px; width: 100%; }
</style>
