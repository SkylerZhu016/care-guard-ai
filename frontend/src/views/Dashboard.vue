<template>
  <div class="dashboard">
    <div class="welcome-section">
      <h2>👋 欢迎回来，{{ userStore.displayName }}</h2>
      <p>{{ currentDate }} · {{ roleLabel }} </p>
    </div>

    <!-- Stats Cards -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6" v-for="stat in statCards" :key="stat.label">
        <el-card shadow="hover" class="stat-card" :style="{ borderTop: `3px solid ${stat.color}` }">
          <div class="stat-content">
            <div class="stat-info">
              <p class="stat-label">{{ stat.label }}</p>
              <p class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</p>
            </div>
            <el-icon :size="36" :color="stat.color" class="stat-icon"><component :is="stat.icon" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Charts and Lists -->
    <el-row :gutter="16" class="content-row">
      <el-col :span="14">
        <el-card class="section-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><Notebook /></el-icon> 待处理就诊</span>
              <el-button text type="primary" @click="router.push('/doctor-workspace')">查看全部</el-button>
            </div>
          </template>
          <el-table :data="stats.recentVisits" style="width: 100%" v-if="stats.recentVisits?.length" size="small">
            <el-table-column prop="patientName" label="患者" />
            <el-table-column prop="riskLevel" label="风险等级" width="100">
              <template #default="{ row }">
                <el-tag :type="riskTagType(row.riskLevel)" size="small">{{ row.riskLevel || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="140" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无待处理就诊" :image-size="80" />
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card class="section-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><WarningFilled /></el-icon> 安全告警</span>
              <el-button text type="primary" @click="router.push('/safety')">查看全部</el-button>
            </div>
          </template>
          <div v-if="stats.recentAlerts?.length" class="alert-list">
            <div v-for="alert in stats.recentAlerts" :key="alert.id" class="alert-item">
              <el-tag :type="alert.severity === 'CRITICAL' ? 'danger' : alert.severity === 'WARNING' ? 'warning' : 'info'" size="small" effect="dark">
                {{ alert.severity }}
              </el-tag>
              <span class="alert-title">{{ alert.title }}</span>
              <span class="alert-time">{{ alert.createdAt }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无安全告警" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- Risk Distribution Chart -->
    <el-row :gutter="16">
      <el-col :span="24">
        <el-card class="section-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><DataAnalysis /></el-icon> 风险分布概览</span>
            </div>
          </template>
          <div class="chart-wrapper">
            <v-chart :option="chartOption" autoresize style="height: 300px" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { dashboardApi } from '@/api'
import type { DashboardStats } from '@/types'
import dayjs from 'dayjs'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([PieChart, TitleComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const router = useRouter()
const userStore = useUserStore()

const stats = reactive<DashboardStats>({
  pendingVisits: 0, activeFollowups: 0, unreviewedAlerts: 0, criticalAlerts: 0,
  recentVisits: [], recentAlerts: [],
})

const currentDate = computed(() => dayjs().format('YYYY年MM月DD日 dddd'))

const roleLabel = computed(() => {
  const map: Record<string, string> = {
    ROLE_PATIENT: '患者', ROLE_DOCTOR: '医务人员', ROLE_FOLLOWUP: '随访人员', ROLE_ADMIN: '管理员',
  }
  return map[userStore.role] || ''
})

const statCards = computed(() => [
  { label: '待处理就诊', value: stats.pendingVisits, icon: 'Notebook', color: '#409eff' },
  { label: '活跃随访', value: stats.activeFollowups, icon: 'Calendar', color: '#67c23a' },
  { label: '未审告警', value: stats.unreviewedAlerts, icon: 'WarningFilled', color: '#e6a23c' },
  { label: '严重告警', value: stats.criticalAlerts, icon: 'CircleCloseFilled', color: '#f56c6c' },
])

const chartOption = computed(() => ({
  tooltip: { trigger: 'item' as const },
  legend: { bottom: '0%' },
  series: [{
    type: 'pie',
    radius: ['40%', '70%'],
    avoidLabelOverlap: true,
    itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
    label: { show: true, formatter: '{b}: {c}' },
    data: [
      { value: stats.pendingVisits || 5, name: '待处理', itemStyle: { color: '#409eff' } },
      { value: stats.activeFollowups || 8, name: '随访中', itemStyle: { color: '#67c23a' } },
      { value: stats.unreviewedAlerts || 2, name: '待审告警', itemStyle: { color: '#e6a23c' } },
      { value: stats.criticalAlerts || 1, name: '严重告警', itemStyle: { color: '#f56c6c' } },
    ],
  }],
}))

function riskTagType(level: number) {
  if (level >= 4) return 'danger'
  if (level >= 3) return 'warning'
  return 'info'
}

function statusTagType(status: string) {
  const map: Record<string, string> = { PENDING: 'info', TRIAGING: 'warning', REVIEW_REQUIRED: 'danger', APPROVED: 'success', FOLLOWUP: 'primary', CLOSED: 'info' }
  return map[status] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = { PENDING: '待处理', TRIAGING: '分诊中', REVIEW_REQUIRED: '待审核', APPROVED: '已通过', FOLLOWUP: '随访中', CLOSED: '已结束' }
  return map[status] || status
}

onMounted(async () => {
  try {
    const res = await dashboardApi.getStats()
    if (res.code === 200) Object.assign(stats, res.data)
  } catch {}
})
</script>

<style scoped>
.dashboard { max-width: 1400px; margin: 0 auto; }
.welcome-section { margin-bottom: 24px; }
.welcome-section h2 { font-size: 24px; color: #1a1a2e; }
.welcome-section p { color: #909399; margin-top: 4px; }
.stats-row { margin-bottom: 16px; }
.stat-card { border-radius: 12px; }
.stat-content { display: flex; align-items: center; justify-content: space-between; }
.stat-label { font-size: 14px; color: #909399; margin-bottom: 8px; }
.stat-value { font-size: 32px; font-weight: 700; }
.content-row { margin-bottom: 16px; }
.section-card { border-radius: 12px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.card-header span { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.alert-list { display: flex; flex-direction: column; gap: 12px; }
.alert-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
.alert-item:last-child { border-bottom: none; }
.alert-title { flex: 1; font-size: 13px; color: #303133; }
.alert-time { font-size: 12px; color: #c0c4cc; white-space: nowrap; }
.chart-wrapper { padding: 8px 0; }
</style>
