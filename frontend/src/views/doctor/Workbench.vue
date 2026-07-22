<template>
  <div class="page">
    <el-alert type="warning" :title="DISCLAIMER" show-icon :closable="false" class="mb-12" />
    <div class="stat-grid">
      <div class="stat-card"><div class="muted">待审核</div><div class="stat-num" style="color:#e6a23c">{{ stats.pending }}</div></div>
      <div class="stat-card"><div class="muted">危急/高风险</div><div class="stat-num" style="color:#f56c6c">{{ stats.highRisk }}</div></div>
      <div class="stat-card"><div class="muted">需患者补充</div><div class="stat-num" style="color:#409eff">{{ stats.needInfo }}</div></div>
    </div>

    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0;flex:1">待审核队列</h3>
        <el-select v-model="filters.riskLevel" placeholder="风险等级" clearable style="width: 130px" @change="load(1)">
          <el-option v-for="(v, k) in riskMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 140px" @change="load(1)">
          <el-option label="待审核" value="PENDING_REVIEW" />
          <el-option label="需补充信息" value="NEED_INFO" />
        </el-select>
        <el-button @click="load(1)"><el-icon><Refresh /></el-icon></el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border @row-click="(r: Visit) => $router.push(`/d/review/${r.id}`)" style="cursor:pointer">
        <el-table-column prop="visitNo" label="问诊单号" width="150" />
        <el-table-column prop="patientName" label="患者" width="110" />
        <el-table-column label="主诉" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.formData?.chiefComplaint }}</template>
        </el-table-column>
        <el-table-column label="风险" width="90">
          <template #default="{ row }"><StatusTag v-if="row.riskLevel" kind="risk" :value="row.riskLevel" /><span v-else>-</span></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag kind="visit" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.submittedAt) }}</template>
        </el-table-column>
        <template #empty><el-empty description="队列已清空，暂无待审核记录" /></template>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next"
        class="mt-12" @current-change="load" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { reviewApi } from '@/api/modules'
import { StatusTag } from '@/components/Tags'
import type { Visit } from '@/types'
import { fmtTime, riskMap, DISCLAIMER } from '@/utils/format'

const loading = ref(false)
const rows = ref<Visit[]>([])
const page = ref(1)
const total = ref(0)
const filters = reactive({ riskLevel: '', status: '' })
const stats = reactive({ pending: 0, highRisk: 0, needInfo: 0 })

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const { data } = await reviewApi.queue({
      page: p - 1, size: 10,
      riskLevel: filters.riskLevel || undefined,
      status: filters.status || undefined
    })
    rows.value = data.content
    total.value = data.totalElements
  } finally { loading.value = false }
}

async function loadStats() {
  try {
    const [a, b, c] = await Promise.all([
      reviewApi.queue({ page: 0, size: 1 }),
      reviewApi.queue({ page: 0, size: 1, riskLevel: 'CRITICAL' }),
      reviewApi.queue({ page: 0, size: 1, status: 'NEED_INFO' })
    ])
    stats.pending = a.data.totalElements
    stats.highRisk = b.data.totalElements
    stats.needInfo = c.data.totalElements
  } catch { /* 统计失败不阻断 */ }
}

onMounted(() => { load(1); loadStats() })
</script>
