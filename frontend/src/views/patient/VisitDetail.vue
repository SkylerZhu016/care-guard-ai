<template>
  <div class="page" v-loading="loading">
    <template v-if="visit">
      <div class="page-card">
        <div class="flex-between">
          <div>
            <h3 class="page-title" style="margin:0">问诊单 {{ visit.visitNo }}</h3>
            <div class="muted mt-4">提交时间：{{ fmtTime(visit.submittedAt) }}</div>
          </div>
          <div>
            <StatusTag kind="visit" :value="visit.status" />
            <StatusTag v-if="visit.riskLevel" kind="risk" :value="visit.riskLevel" style="margin-left:8px" />
          </div>
        </div>
      </div>

      <!-- 处理中：流水线进度 -->
      <div v-if="processing" class="page-card">
        <h4 class="page-title">AI 处理进度</h4>
        <PipelineProgress :visit-id="visit.id" @finished="onPipelineFinished" />
      </div>

      <!-- 需补充信息 -->
      <el-alert v-if="visit.status === 'NEED_INFO'" type="warning" class="mb-12" show-icon :closable="false"
        title="医务人员要求您补充信息">
        <template #default>
          <el-button size="small" type="warning" @click="$router.push(`/p/visits/${visit.id}/supplement`)">去补充</el-button>
        </template>
      </el-alert>

      <!-- 原始填写内容 -->
      <div class="page-card">
        <h4 class="page-title">我的填写内容</h4>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="主诉" :span="2">{{ visit.formData.chiefComplaint }}</el-descriptions-item>
          <el-descriptions-item label="起病时间">{{ fmtDate(visit.formData.onsetTime) }}</el-descriptions-item>
          <el-descriptions-item label="持续时间">{{ visit.formData.duration || '-' }}</el-descriptions-item>
          <el-descriptions-item label="严重程度">{{ visit.formData.severity ? severityMap[visit.formData.severity] : '-' }}</el-descriptions-item>
          <el-descriptions-item label="特殊人群">{{ specialGroupMap[visit.formData.basic?.specialGroup || 'NONE'] }}</el-descriptions-item>
          <el-descriptions-item label="伴随症状" :span="2">{{ visit.formData.accompanying?.join('、') || '无' }}</el-descriptions-item>
          <el-descriptions-item label="既往史" :span="2">{{ visit.formData.pastHistory || '无' }}</el-descriptions-item>
          <el-descriptions-item label="过敏史" :span="2">{{ visit.formData.allergyHistory || '无' }}</el-descriptions-item>
          <el-descriptions-item label="用药情况" :span="2">{{ visit.formData.medication || '无' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 已审核：建议（患者仅此时可见 AI 内容） -->
      <template v-if="visit.status === 'REVIEWED' && triage">
        <div class="page-card">
          <h4 class="page-title">医务人员审核后的建议</h4>
          <el-alert type="warning" :title="triage.disclaimer || DISCLAIMER" show-icon :closable="false" class="mb-12" />
          <el-descriptions :column="1" border>
            <el-descriptions-item label="风险等级">
              <StatusTag kind="risk" :value="triage.riskLevel || 'LOW'" />
            </el-descriptions-item>
            <el-descriptions-item label="风险摘要">{{ triage.riskSummary || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="triage.riskPoints?.length" class="mt-12">
            <div v-for="(rp, i) in triage.riskPoints" :key="i" class="risk-item">
              <StatusTag kind="risk" :value="rp.severity || 'LOW'" />
              <span style="margin-left:8px">{{ rp.point }}</span>
              <div class="muted mt-4">{{ rp.basis }}</div>
            </div>
          </div>
        </div>
        <div class="page-card" v-if="triage.citations?.length">
          <h4 class="page-title">参考指南（公开资料）</h4>
          <CitationList :citations="triage.citations" />
        </div>
      </template>

      <!-- 状态流转时间线 -->
      <div class="page-card">
        <h4 class="page-title">处理记录</h4>
        <el-timeline>
          <el-timeline-item v-for="log in statusLogs" :key="log.id" :timestamp="fmtTime(log.createdAt)">
            {{ visitStatusMap[log.toStatus]?.label || log.toStatus }}
            <span v-if="log.reason" class="muted"> — {{ log.reason }}</span>
          </el-timeline-item>
        </el-timeline>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { visitApi, triageApi } from '@/api/modules'
import { StatusTag } from '@/components/Tags'
import PipelineProgress from '@/components/PipelineProgress.vue'
import CitationList from '@/components/CitationList.vue'
import type { StatusLog, TriageDetail, Visit } from '@/types'
import { fmtDate, fmtTime, severityMap, specialGroupMap, visitStatusMap, DISCLAIMER } from '@/utils/format'

const route = useRoute()
const visitId = Number(route.params.id)
const loading = ref(true)
const visit = ref<Visit | null>(null)
const triage = ref<TriageDetail | null>(null)
const statusLogs = ref<StatusLog[]>([])

const processing = computed(() =>
  visit.value && ['SUBMITTED', 'STRUCTURING', 'RULE_SCREENED', 'AI_ANALYZING'].includes(visit.value.status)
)

async function load() {
  loading.value = true
  try {
    const { data } = await visitApi.detail(visitId)
    visit.value = data
    const [logs, tg] = await Promise.all([
      visitApi.statusLogs(visitId).catch(() => ({ data: [] as StatusLog[] })),
      data.status === 'REVIEWED' ? triageApi.detail(visitId).catch(() => ({ data: null })) : Promise.resolve({ data: null })
    ])
    statusLogs.value = logs.data
    triage.value = tg.data
  } finally { loading.value = false }
}

function onPipelineFinished() { load() }
onMounted(load)
</script>

<style scoped>
.risk-item { border-left: 3px solid #e6a23c; padding: 6px 12px; margin-bottom: 10px; background: #fafafa; }
.mt-4 { margin-top: 4px; }
</style>
