<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0;flex:1">Agent 运行记录</h3>
        <el-select v-model="status" placeholder="状态" clearable style="width: 150px" @change="load(1)">
          <el-option v-for="(v, k) in runStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button @click="load(1)"><el-icon><Refresh /></el-icon></el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="id" label="Run ID" width="90" />
        <el-table-column prop="visitId" label="问诊单" width="90" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }"><StatusTag kind="run" :value="row.status" /></template>
        </el-table-column>
        <el-table-column prop="currentStep" label="当前步骤" width="110">
          <template #default="{ row }">{{ stepNameMap[row.currentStep] || row.currentStep || '-' }}</template>
        </el-table-column>
        <el-table-column prop="totalTokens" label="Tokens" width="90" />
        <el-table-column prop="modelName" label="模型" width="130" />
        <el-table-column label="耗时" width="110">
          <template #default="{ row }">{{ duration(row) }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }"><el-button size="small" @click="open(row.id)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next" class="mt-12" @current-change="load" />
    </div>

    <el-drawer v-model="drawer" title="运行详情" size="60%">
      <template v-if="detail">
        <el-descriptions :column="3" border class="mb-12">
          <el-descriptions-item label="状态"><StatusTag kind="run" :value="detail.status" /></el-descriptions-item>
          <el-descriptions-item label="模型">{{ detail.modelName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Tokens">{{ detail.totalTokens }}</el-descriptions-item>
          <el-descriptions-item label="知识库版本">{{ detail.knowledgeVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="规则版本">{{ detail.ruleVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Prompt版本">{{ Object.values(detail.promptVersions || {}).join(', ') || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="detail.errorMessage" type="error" :title="detail.errorMessage" :closable="false" class="mb-12" show-icon />
        <el-timeline>
          <el-timeline-item v-for="s in detail.steps" :key="s.id"
            :type="s.status === 'SUCCESS' ? 'success' : s.status === 'FAILED' ? 'danger' : 'primary'"
            :timestamp="`${stepNameMap[s.step] || s.step} · ${s.durationMs ?? '-'}ms · ${s.tokens ?? 0} tokens`">
            <div class="flex-between">
              <b>{{ stepNameMap[s.step] || s.step }}</b>
              <el-tag size="small" :type="s.status === 'SUCCESS' ? 'success' : s.status === 'FAILED' ? 'danger' : 'info'">{{ s.status }}</el-tag>
            </div>
            <el-collapse class="mt-4">
              <el-collapse-item title="输入" name="1"><div class="json-view">{{ pretty(s.inputJson) }}</div></el-collapse-item>
              <el-collapse-item title="输出" name="2"><div class="json-view">{{ pretty(s.outputJson) }}</div></el-collapse-item>
            </el-collapse>
            <el-alert v-if="s.error" type="error" :title="s.error" :closable="false" class="mt-4" />
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { agentRunApi } from '@/api/modules'
import { StatusTag } from '@/components/Tags'
import type { AgentRun } from '@/types'
import { fmtTime, runStatusMap, stepNameMap } from '@/utils/format'

const loading = ref(false)
const rows = ref<AgentRun[]>([])
const page = ref(1)
const total = ref(0)
const status = ref('')
const drawer = ref(false)
const detail = ref<AgentRun | null>(null)

function duration(r: AgentRun) {
  if (!r.startedAt || !r.finishedAt) return '-'
  return `${dayjs(r.finishedAt).diff(dayjs(r.startedAt))}ms`
}
function pretty(v: unknown) {
  return v ? JSON.stringify(v, null, 2) : '-'
}

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const { data } = await agentRunApi.page({ page: p - 1, size: 10, status: status.value || undefined })
    rows.value = data.content
    total.value = data.totalElements
  } finally { loading.value = false }
}

async function open(id: number) {
  const { data } = await agentRunApi.detail(id)
  detail.value = data
  drawer.value = true
}

onMounted(() => load(1))
</script>

<style scoped>
.mt-4 { margin-top: 4px; }
</style>
