<template>
  <div class="page" v-loading="loading">
    <template v-if="task">
      <div class="page-card">
        <div class="flex-between">
          <h3 class="page-title" style="margin:0">随访任务 #{{ task.id }}</h3>
          <div>
            <StatusTag kind="task" :value="task.status" />
            <StatusTag v-if="task.riskLevel" kind="risk" :value="task.riskLevel" style="margin-left:8px" />
          </div>
        </div>
        <el-descriptions :column="2" border class="mt-12">
          <el-descriptions-item label="任务">{{ task.title }}</el-descriptions-item>
          <el-descriptions-item label="患者">{{ task.patientName }}</el-descriptions-item>
          <el-descriptions-item label="截止日期">{{ task.dueDate }}</el-descriptions-item>
          <el-descriptions-item label="内容" :span="2">{{ task.content || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="patientFeedback" type="info" class="mt-12" show-icon :closable="false"
          :title="'患者预填反馈：' + patientFeedback" />
      </div>

      <div class="page-card" v-if="['ASSIGNED', 'IN_PROGRESS', 'DELAYED'].includes(task.status)">
        <h4 class="page-title">填写随访记录</h4>
        <el-form label-width="90px" style="max-width: 720px">
          <el-form-item label="联系结果" required>
            <el-radio-group v-model="record.contactResult">
              <el-radio value="REACHED">已联系</el-radio>
              <el-radio value="UNREACHED">未联系上</el-radio>
              <el-radio value="REFUSED">拒绝随访</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="症状变化" required>
            <el-radio-group v-model="record.symptomChange">
              <el-radio v-for="(v, k) in symptomChangeMap" :key="k" :value="k">{{ v }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="随访记录"><el-input v-model="record.note" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="患者反馈"><el-input v-model="record.feedback" type="textarea" :rows="2" /></el-form-item>
          <el-form-item>
            <el-button type="success" :loading="acting" @click="complete">完成任务</el-button>
            <el-button type="warning" @click="delayDlg = true">延期</el-button>
            <el-button type="info" @click="lostDlg = true">失联</el-button>
            <el-button type="danger" @click="escDlg = true">风险升级</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="page-card" v-if="existingRecord">
        <h4 class="page-title">随访记录</h4>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="联系结果">{{ existingRecord.contactResult }}</el-descriptions-item>
          <el-descriptions-item label="症状变化">{{ symptomChangeMap[existingRecord.symptomChange || ''] || '-' }}</el-descriptions-item>
          <el-descriptions-item label="记录">{{ existingRecord.note || '-' }}</el-descriptions-item>
          <el-descriptions-item label="患者反馈">{{ existingRecord.feedback || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </template>

    <el-dialog v-model="delayDlg" title="任务延期" width="420px">
      <el-form label-width="90px">
        <el-form-item label="新截止日"><el-date-picker v-model="delayForm.newDueDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="delayForm.reason" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="delayDlg = false">取消</el-button><el-button type="primary" @click="doDelay">确认</el-button></template>
    </el-dialog>
    <el-dialog v-model="lostDlg" title="标记失联" width="420px">
      <el-input v-model="lostReason" type="textarea" :rows="3" placeholder="失联情况说明" />
      <template #footer><el-button @click="lostDlg = false">取消</el-button><el-button type="primary" @click="doLost">确认</el-button></template>
    </el-dialog>
    <el-dialog v-model="escDlg" title="风险升级" width="420px">
      <el-alert type="warning" title="升级后将生成安全告警并通知管理人员" show-icon :closable="false" class="mb-12" />
      <el-input v-model="escReason" type="textarea" :rows="3" placeholder="升级原因，如：症状明显加重" />
      <template #footer><el-button @click="escDlg = false">取消</el-button><el-button type="danger" @click="doEscalate">确认升级</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { followupApi } from '@/api/modules'
import { StatusTag } from '@/components/Tags'
import type { FollowupRecord, FollowupTask } from '@/types'
import { symptomChangeMap } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.id)
const loading = ref(true)
const acting = ref(false)
const task = ref<FollowupTask | null>(null)
const existingRecord = ref<FollowupRecord | null>(null)
const patientFeedback = ref('')
const record = reactive({ contactResult: 'REACHED', symptomChange: 'STABLE', note: '', feedback: '' })
const delayDlg = ref(false)
const lostDlg = ref(false)
const escDlg = ref(false)
const delayForm = reactive({ newDueDate: '', reason: '' })
const lostReason = ref('')
const escReason = ref('')

onMounted(async () => {
  try {
    const { data } = await followupApi.taskDetail(taskId)
    task.value = data
    // 读取患者预填反馈（本机草稿）
    const fb = localStorage.getItem(`followup_feedback_${taskId}`)
    if (fb) {
      const parsed = JSON.parse(fb)
      patientFeedback.value = `${symptomChangeMap[parsed.symptomChange] || ''}；${parsed.feedback || ''}`
      record.feedback = parsed.feedback || ''
      record.symptomChange = parsed.symptomChange || 'STABLE'
    }
    if (data.status === 'COMPLETED') {
      const { data: recs } = await followupApi.records(data.patientId)
      existingRecord.value = recs.find((r) => r.taskId === taskId) || null
    }
  } finally { loading.value = false }
})

async function complete() {
  acting.value = true
  try {
    await followupApi.complete(taskId, { ...record })
    localStorage.removeItem(`followup_feedback_${taskId}`)
    ElMessage.success('任务已完成')
    router.push('/f/board')
  } finally { acting.value = false }
}
async function doDelay() {
  await followupApi.delay(taskId, { ...delayForm })
  ElMessage.success('已延期'); delayDlg.value = false; router.push('/f/board')
}
async function doLost() {
  await followupApi.lost(taskId, { reason: lostReason.value })
  ElMessage.success('已标记失联'); lostDlg.value = false; router.push('/f/board')
}
async function doEscalate() {
  await followupApi.escalate(taskId, { reason: escReason.value })
  ElMessage.warning('已升级，安全告警已生成'); escDlg.value = false; router.push('/f/board')
}
</script>
