<template>
  <div class="followup">
    <el-card class="page-header">
      <h2><el-icon><Calendar /></el-icon> 随访管理</h2>
      <p class="page-desc">管理慢病随访任务 · 查看随访计划和完成情况</p>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="24">
        <el-card class="section-card">
          <template #header>
            <span><el-icon><List /></el-icon> 随访计划列表</span>
          </template>
          <el-table :data="plans" style="width:100%">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="planName" label="计划名称" min-width="160" />
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column label="间隔/次数" width="120">
              <template #default="{ row }">每{{ row.intervalDays }}天 / 共{{ row.totalTimes }}次</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" @click="viewTasks(row)">查看任务</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- Tasks Dialog -->
    <el-dialog v-model="taskDialogVisible" title="随访任务" width="700px">
      <el-table :data="tasks" style="width:100%">
        <el-table-column prop="dueDate" label="到期日" width="120" />
        <el-table-column label="问卷" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.questionnaire }}</template>
        </el-table-column>
        <el-table-column label="患者反馈" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.patientResponse || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'OVERDUE' ? 'danger' : 'info'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="completeTask(row)">完成</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { followupApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FollowupPlan, FollowupTask } from '@/types'

const plans = ref<FollowupPlan[]>([])
const tasks = ref<FollowupTask[]>([])
const taskDialogVisible = ref(false)

async function fetchPlans() {
  try {
    const res = await followupApi.getPlans(0) // Will get all - for demo
    if (res.code === 200) plans.value = res.data
  } catch {}
}

async function viewTasks(plan: FollowupPlan) {
  try {
    const res = await followupApi.getTasks(plan.id)
    if (res.code === 200) {
      tasks.value = res.data
      taskDialogVisible.value = true
    }
  } catch {}
}

async function completeTask(task: FollowupTask) {
  try {
    const { value } = await ElMessageBox.prompt('请输入随访反馈', '完成随访任务', { inputType: 'textarea' })
    if (value) {
      const res = await followupApi.completeTask(task.id, value)
      if (res.code === 200) {
        ElMessage.success('随访完成')
        task.status = 'COMPLETED'
        await viewTasks(tasks.value[0]?.planId ? { id: tasks.value[0].planId } as FollowupPlan : {} as FollowupPlan)
      }
    }
  } catch {}
}

onMounted(fetchPlans)
</script>

<style scoped>
.followup { max-width: 1400px; margin: 0 auto; }
.page-header { border-radius: 12px; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
.page-desc { color: #909399; font-size: 13px; margin-top: 6px; }
.section-card { border-radius: 12px; }
</style>
