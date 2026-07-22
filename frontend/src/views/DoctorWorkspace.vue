<template>
  <div class="doctor-workspace">
    <el-card class="page-header">
      <h2><el-icon><Notebook /></el-icon> 医生工作台</h2>
      <p class="page-desc">待处理就诊队列 · 审核结构化病历与风险提示</p>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="24">
        <el-card class="section-card">
          <template #header>
            <div class="flex-between">
              <span><el-icon><List /></el-icon> 待处理队列 ({{ visits.length }})</span>
              <el-radio-group v-model="filterStatus" size="small">
                <el-radio-button value="all">全部</el-radio-button>
                <el-radio-button value="REVIEW_REQUIRED">待审核</el-radio-button>
                <el-radio-button value="APPROVED">已通过</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-table :data="filteredVisits" style="width:100%" @row-click="selectVisit">
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expanded-detail">
                  <el-descriptions :column="2" border size="small">
                    <el-descriptions-item label="主诉">{{ row.chiefComplaint }}</el-descriptions-item>
                    <el-descriptions-item label="风险等级">
                      <el-tag :type="riskTag(row.riskLevel)" size="small">{{ row.riskLevel || '-' }}</el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="分诊结果">{{ row.triageResult || '待分诊' }}</el-descriptions-item>
                    <el-descriptions-item label="医生备注">{{ row.doctorNotes || '-' }}</el-descriptions-item>
                  </el-descriptions>
                  <div class="action-bar" v-if="row.status === 'REVIEW_REQUIRED'">
                    <el-input v-model="reviewNotes[row.id]" placeholder="审核意见" style="width:300px" />
                    <el-button type="primary" size="small" @click.stop="approveVisit(row.id)">通过审核</el-button>
                    <el-button type="primary" size="small" @click.stop="createFollowup(row)">创建随访</el-button>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="patient.name" label="患者姓名" width="120" />
            <el-table-column prop="patient.age" label="年龄" width="60" />
            <el-table-column prop="chiefComplaint" label="主诉" min-width="200" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="riskLevel" label="风险" width="80">
              <template #default="{ row }">
                <el-tag :type="riskTag(row.riskLevel)" size="small">{{ row.riskLevel || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="140" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { consultApi, followupApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Visit } from '@/types'

const visits = ref<Visit[]>([])
const filterStatus = ref('all')
const reviewNotes = ref<Record<number, string>>({})

const filteredVisits = computed(() => {
  if (filterStatus.value === 'all') return visits.value
  return visits.value.filter(v => v.status === filterStatus.value)
})

async function fetchVisits() {
  try {
    const res = await consultApi.getPending()
    if (res.code === 200) visits.value = res.data
  } catch {}
}

async function approveVisit(id: number) {
  const notes = reviewNotes.value[id] || '审核通过'
  const res = await consultApi.approve(id, notes)
  if (res.code === 200) {
    ElMessage.success('审核完成')
    reviewNotes.value[id] = ''
    fetchVisits()
  }
}

async function createFollowup(visit: Visit) {
  try {
    const res = await followupApi.createPlan({
      visitId: visit.id,
      name: `${visit.patient?.name || '患者'}随访计划`,
    })
    if (res.code === 200) {
      ElMessage.success('随访计划已创建')
      fetchVisits()
    }
  } catch {}
}

function selectVisit(row: Visit) {
  // expand handling is done by el-table
}

function riskTag(level: number) {
  if (level >= 4) return 'danger'
  if (level >= 3) return 'warning'
  return 'info'
}

function statusTag(s: string) {
  const map: Record<string, string> = { PENDING: 'info', TRIAGING: 'warning', REVIEW_REQUIRED: 'danger', APPROVED: 'success', FOLLOWUP: 'primary', CLOSED: 'info' }
  return map[s] || 'info'
}

function statusLabel(s: string) {
  const map: Record<string, string> = { PENDING: '待处理', TRIAGING: '分诊中', REVIEW_REQUIRED: '待审核', APPROVED: '已通过', FOLLOWUP: '随访中', CLOSED: '已结束' }
  return map[s] || s
}

onMounted(fetchVisits)
</script>

<style scoped>
.doctor-workspace { max-width: 1400px; margin: 0 auto; }
.page-header { border-radius: 12px; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
.page-desc { color: #909399; font-size: 13px; margin-top: 6px; }
.section-card { border-radius: 12px; }
.flex-between { display: flex; align-items: center; justify-content: space-between; }
.expanded-detail { padding: 16px; }
.action-bar { margin-top: 12px; display: flex; gap: 8px; align-items: center; }
</style>
