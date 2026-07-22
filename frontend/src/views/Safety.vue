<template>
  <div class="safety">
    <el-card class="page-header">
      <h2><el-icon><WarningFilled /></el-icon> AI 安全监控面板</h2>
      <p class="page-desc">监控红旗症状、提示词攻击、隐私泄露等安全事件</p>
    </el-card>

    <el-row :gutter="16" class="stats-row">
      <el-col :span="6" v-for="s in statCards" :key="s.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <p class="stat-label">{{ s.label }}</p>
              <p class="stat-value" :style="{ color: s.color }">{{ s.value }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="section-card">
      <template #header>
        <div class="flex-between">
          <span><el-icon><List /></el-icon> 安全告警列表</span>
          <el-switch v-model="unreviewedOnly" active-text="仅未审" @change="fetchAlerts" />
        </div>
      </template>
      <el-table :data="alerts" style="width:100%">
        <el-table-column prop="title" label="告警标题" min-width="180" />
        <el-table-column prop="alertType" label="类型" width="140">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.alertType)" size="small">{{ typeLabel(row.alertType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="severity" label="严重程度" width="100">
          <template #default="{ row }">
            <el-tag :type="row.severity === 'CRITICAL' ? 'danger' : row.severity === 'WARNING' ? 'warning' : 'info'" size="small" effect="dark">
              {{ row.severity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
        <el-table-column label="已审" width="70">
          <template #default="{ row }">
            <el-tag :type="row.reviewed ? 'success' : 'warning'" size="small">{{ row.reviewed ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-if="!row.reviewed" size="small" type="primary" @click="reviewAlert(row.id)">审核</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { safetyApi } from '@/api'
import { ElMessage } from 'element-plus'
import type { SafetyAlert } from '@/types'

const alerts = ref<SafetyAlert[]>([])
const unreviewedOnly = ref(false)

const statCards = reactive([
  { label: '总告警数', value: 0, color: '#f56c6c' },
  { label: '红旗症状', value: 0, color: '#e6a23c' },
  { label: '提示词攻击', value: 0, color: '#409eff' },
  { label: '隐私泄露', value: 0, color: '#67c23a' },
])

async function fetchAlerts() {
  try {
    const res = await safetyApi.getAlerts(unreviewedOnly.value)
    if (res.code === 200) {
      alerts.value = res.data
      statCards[0].value = res.data.length
      statCards[1].value = res.data.filter(a => a.alertType === 'RED_FLAG_SYMPTOM').length
      statCards[2].value = res.data.filter(a => a.alertType === 'PROMPT_INJECTION').length
      statCards[3].value = res.data.filter(a => a.alertType === 'PRIVACY_LEAK').length
    }
  } catch {}
}

async function reviewAlert(id: number) {
  try {
    const res = await safetyApi.review(id)
    if (res.code === 200) {
      ElMessage.success('告警已审核')
      fetchAlerts()
    }
  } catch {}
}

function typeTag(type: string) {
  const map: Record<string, string> = {
    RED_FLAG_SYMPTOM: 'danger',
    DRUG_INTERACTION: 'warning',
    PROMPT_INJECTION: 'warning',
    UNAUTHORIZED_DIAGNOSIS: 'danger',
    PRIVACY_LEAK: 'danger',
    MODEL_HALLUCINATION: 'info',
  }
  return map[type] || 'info'
}

function typeLabel(type: string) {
  const map: Record<string, string> = {
    RED_FLAG_SYMPTOM: '红旗症状',
    DRUG_INTERACTION: '药物相互作用',
    PROMPT_INJECTION: '提示词攻击',
    UNAUTHORIZED_DIAGNOSIS: '越权诊断',
    PRIVACY_LEAK: '隐私泄露',
    MODEL_HALLUCINATION: '模型幻觉',
  }
  return map[type] || type
}

onMounted(fetchAlerts)
</script>

<style scoped>
.safety { max-width: 1400px; margin: 0 auto; }
.page-header { border-radius: 12px; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
.page-desc { color: #909399; font-size: 13px; margin-top: 6px; }
.stats-row { margin-bottom: 16px; }
.stat-card { border-radius: 12px; }
.stat-content { display: flex; align-items: center; justify-content: space-between; }
.stat-label { font-size: 14px; color: #909399; margin-bottom: 8px; }
.stat-value { font-size: 28px; font-weight: 700; }
.section-card { border-radius: 12px; }
.flex-between { display: flex; align-items: center; justify-content: space-between; }
</style>
