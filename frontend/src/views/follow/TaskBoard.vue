<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0;flex:1">随访任务看板</h3>
        <el-radio-group v-model="tab" @change="load(1)">
          <el-radio-button value="today">今日任务</el-radio-button>
          <el-radio-button value="overdue">逾期</el-radio-button>
          <el-radio-button value="high">高风险</el-radio-button>
          <el-radio-button value="all">全部</el-radio-button>
        </el-radio-group>
        <el-select v-model="status" placeholder="状态" clearable style="width: 130px" @change="load(1)">
          <el-option v-for="(v, k) in taskStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="title" label="任务" min-width="150" />
        <el-table-column prop="patientName" label="患者" width="110" />
        <el-table-column prop="dueDate" label="截止日期" width="110">
          <template #default="{ row }">
            <span :style="{ color: overdue(row) ? '#f56c6c' : 'inherit' }">{{ row.dueDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="风险" width="90">
          <template #default="{ row }"><StatusTag v-if="row.riskLevel" kind="risk" :value="row.riskLevel" /><span v-else>-</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag kind="task" :value="row.status" /></template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="负责人" width="100">
          <template #default="{ row }">{{ row.assigneeName || '未认领' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="claim(row.id)">认领</el-button>
            <el-button v-if="row.status === 'ASSIGNED'" size="small" type="warning" @click="start(row.id)">开始</el-button>
            <el-button size="small" @click="$router.push(`/f/task/${row.id}`)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无任务" /></template>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next" class="mt-12" @current-change="load" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { followupApi } from '@/api/modules'
import { StatusTag } from '@/components/Tags'
import type { FollowupTask } from '@/types'
import { taskStatusMap } from '@/utils/format'

const loading = ref(false)
const rows = ref<FollowupTask[]>([])
const page = ref(1)
const total = ref(0)
const tab = ref('today')
const status = ref('')

function overdue(t: FollowupTask) {
  return !['COMPLETED', 'CANCELLED'].includes(t.status) && dayjs(t.dueDate).isBefore(dayjs(), 'day')
}

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const params: Record<string, string | number | undefined> = { page: p - 1, size: 10, status: status.value || undefined }
    if (tab.value === 'today') params.dueBefore = dayjs().format('YYYY-MM-DD')
    if (tab.value === 'overdue') { params.dueBefore = dayjs().subtract(1, 'day').format('YYYY-MM-DD'); params.status = params.status || 'PENDING' }
    if (tab.value === 'high') params.riskLevel = 'HIGH'
    const { data } = await followupApi.taskPage(params)
    rows.value = data.content
    total.value = data.totalElements
  } finally { loading.value = false }
}

async function claim(id: number) {
  await followupApi.claim(id)
  ElMessage.success('已认领')
  load()
}
async function start(id: number) {
  await followupApi.start(id)
  ElMessage.success('已开始处理')
  load()
}

onMounted(() => load(1))
</script>
