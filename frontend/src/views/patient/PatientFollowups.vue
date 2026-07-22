<template>
  <div class="page">
    <div class="page-card">
      <h3 class="page-title">我的随访计划</h3>
      <el-table v-loading="loading" :data="plans" border>
        <el-table-column prop="planName" label="计划名称" min-width="140" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag kind="plan" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="随访频率" width="110">
          <template #default="{ row }">每 {{ row.intervalDays }} 天</template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="110" />
        <el-table-column label="任务进度" min-width="160">
          <template #default="{ row }">
            <el-progress v-if="row.tasks?.length" :percentage="progress(row)" />
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }"><el-button size="small" @click="openPlan(row.id)">查看</el-button></template>
        </el-table-column>
        <template #empty><el-empty description="暂无随访计划" /></template>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next"
        class="mt-12" @current-change="load" />
    </div>

    <el-drawer v-model="drawer" title="计划详情" size="55%">
      <template v-if="plan">
        <el-descriptions :column="2" border class="mb-12">
          <el-descriptions-item label="计划名称">{{ plan.planName }}</el-descriptions-item>
          <el-descriptions-item label="状态"><StatusTag kind="plan" :value="plan.status" /></el-descriptions-item>
          <el-descriptions-item label="结束条件" :span="2">{{ plan.endCondition || '-' }}</el-descriptions-item>
          <el-descriptions-item v-for="(it, i) in plan.items" :key="i" :label="it.title" :span="2">{{ it.content }}</el-descriptions-item>
        </el-descriptions>
        <h4>随访任务</h4>
        <el-table :data="plan.tasks" border size="small">
          <el-table-column prop="title" label="任务" min-width="120" />
          <el-table-column prop="dueDate" label="截止" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><StatusTag kind="task" :value="row.status" /></template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button size="small" @click="$router.push(`/p/followup-task/${row.id}`)">查看/反馈</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { followupApi } from '@/api/modules'
import { StatusTag } from '@/components/Tags'
import type { FollowupPlan } from '@/types'

const loading = ref(false)
const plans = ref<FollowupPlan[]>([])
const page = ref(1)
const total = ref(0)
const drawer = ref(false)
const plan = ref<FollowupPlan | null>(null)

function progress(p: FollowupPlan) {
  const done = (p.tasks || []).filter((t) => t.status === 'COMPLETED').length
  return p.tasks?.length ? Math.round((done / p.tasks.length) * 100) : 0
}

async function load() {
  loading.value = true
  try {
    const { data } = await followupApi.planPage({ page: page.value - 1, size: 10 })
    plans.value = data.content
    total.value = data.totalElements
  } finally { loading.value = false }
}

async function openPlan(id: number) {
  const { data } = await followupApi.planDetail(id)
  plan.value = data
  drawer.value = true
}

onMounted(load)
</script>
