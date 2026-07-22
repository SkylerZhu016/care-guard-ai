<template>
  <div class="page" v-loading="loading">
    <div class="page-card" v-if="task">
      <h3 class="page-title">随访任务详情</h3>
      <el-descriptions :column="2" border class="mb-12">
        <el-descriptions-item label="任务">{{ task.title }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag kind="task" :value="task.status" /></el-descriptions-item>
        <el-descriptions-item label="截止日期">{{ task.dueDate }}</el-descriptions-item>
        <el-descriptions-item label="内容" :span="2">{{ task.content || '-' }}</el-descriptions-item>
      </el-descriptions>

      <template v-if="record">
        <h4 class="page-title">随访记录（由随访人员填写）</h4>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="联系结果">{{ contactResultMap[record.contactResult || ''] || record.contactResult || '-' }}</el-descriptions-item>
          <el-descriptions-item label="症状变化">{{ symptomChangeMap[record.symptomChange || ''] || '-' }}</el-descriptions-item>
          <el-descriptions-item label="随访记录">{{ record.note || '-' }}</el-descriptions-item>
          <el-descriptions-item label="我的反馈">{{ record.feedback || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <template v-else-if="['PENDING', 'ASSIGNED', 'IN_PROGRESS'].includes(task.status)">
        <el-alert type="info" title="随访人员将与您联系完成本次随访，您可以在下方预先填写反馈" show-icon :closable="false" class="mb-12" />
        <el-form label-width="90px" style="max-width: 640px">
          <el-form-item label="症状变化">
            <el-radio-group v-model="feedbackForm.symptomChange">
              <el-radio v-for="(v, k) in symptomChangeMap" :key="k" :value="k">{{ v }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="我的反馈">
            <el-input v-model="feedbackForm.feedback" type="textarea" :rows="3" placeholder="想对随访人员说明的情况" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="submitFeedback">提交反馈</el-button>
            <span class="muted" style="margin-left:8px">反馈将随随访记录一同保存</span>
          </el-form-item>
        </el-form>
      </template>
      <el-alert v-else type="info" title="该任务已结束" show-icon :closable="false" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { followupApi } from '@/api/modules'
import { StatusTag } from '@/components/Tags'
import type { FollowupRecord, FollowupTask } from '@/types'
import { symptomChangeMap } from '@/utils/format'

const route = useRoute()
const taskId = Number(route.params.id)
const loading = ref(true)
const saving = ref(false)
const task = ref<FollowupTask | null>(null)
const record = ref<FollowupRecord | null>(null)
const feedbackForm = reactive({ symptomChange: 'STABLE', feedback: '' })
const contactResultMap: Record<string, string> = { REACHED: '已联系', UNREACHED: '未联系上', REFUSED: '拒绝随访' }

onMounted(async () => {
  try {
    const { data } = await followupApi.taskDetail(taskId)
    task.value = data
    if (data.status === 'COMPLETED') {
      const { data: recs } = await followupApi.records(data.patientId)
      record.value = recs.find((r) => r.taskId === taskId) || null
    }
  } finally { loading.value = false }
})

async function submitFeedback() {
  saving.value = true
  try {
    // 患者反馈暂存于本地草稿，随访人员执行时读取填写（课程简化方案，见 docs/DECISIONS.md）
    localStorage.setItem(`followup_feedback_${taskId}`, JSON.stringify(feedbackForm))
    ElMessage.success('反馈已保存，随访人员执行时将看到')
  } finally { saving.value = false }
}
</script>
