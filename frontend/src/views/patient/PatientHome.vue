<template>
  <div class="page">
    <el-alert type="warning" :title="DISCLAIMER" show-icon :closable="false" class="mb-12" />
    <div class="stat-grid">
      <div class="stat-card">
        <div class="muted">模拟患者档案</div>
        <div class="stat-num">{{ stats.patients }}</div>
      </div>
      <div class="stat-card">
        <div class="muted">预问诊记录</div>
        <div class="stat-num">{{ stats.visits }}</div>
      </div>
      <div class="stat-card">
        <div class="muted">处理中</div>
        <div class="stat-num" style="color:#e6a23c">{{ stats.processing }}</div>
      </div>
      <div class="stat-card">
        <div class="muted">待完成随访任务</div>
        <div class="stat-num" style="color:#409eff">{{ stats.tasks }}</div>
      </div>
    </div>
    <div class="page-card">
      <h3 class="page-title">快捷操作</h3>
      <el-button type="primary" @click="$router.push('/p/visit-new')">
        <el-icon style="margin-right:4px"><EditPen /></el-icon>新建预问诊
      </el-button>
      <el-button @click="$router.push('/p/archive')">管理患者档案</el-button>
      <el-button @click="$router.push('/p/visits')">查看问诊记录</el-button>
      <el-button @click="$router.push('/p/followups')">我的随访</el-button>
    </div>
    <div class="page-card">
      <h3 class="page-title">使用说明</h3>
      <ol style="line-height: 2; color: #606266; margin: 0; padding-left: 18px">
        <li>在「模拟患者档案」中维护演示用患者信息（全部为合成数据）。</li>
        <li>在「新建预问诊」中分步骤填写症状信息，可随时保存草稿。</li>
        <li>提交后系统自动进行症状结构化、规则预筛、指南检索与 AI 风险分析。</li>
        <li>医务人员审核通过后，您可以在「我的预问诊」中查看建议，并按期完成随访任务。</li>
      </ol>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { patientApi, visitApi, followupApi } from '@/api/modules'
import { DISCLAIMER } from '@/utils/format'

const stats = reactive({ patients: 0, visits: 0, processing: 0, tasks: 0 })

onMounted(async () => {
  try {
    const [p, v, pr, t] = await Promise.all([
      patientApi.page({ page: 0, size: 1 }),
      visitApi.page({ page: 0, size: 1 }),
      visitApi.page({ page: 0, size: 1, status: 'AI_ANALYZING' }),
      followupApi.taskPage({ page: 0, size: 1, status: 'PENDING' })
    ])
    stats.patients = p.data.totalElements
    stats.visits = v.data.totalElements
    stats.processing = pr.data.totalElements
    stats.tasks = t.data.totalElements
  } catch { /* 统计失败不阻断 */ }
})
</script>
