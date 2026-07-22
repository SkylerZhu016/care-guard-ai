<template>
  <div class="page" v-loading="loading">
    <template v-if="visit">
      <div class="page-card">
        <div class="flex-between">
          <div>
            <h3 class="page-title" style="margin:0">审核问诊单 {{ visit.visitNo }}</h3>
            <span class="muted">患者：{{ visit.patientName }} · 提交：{{ fmtTime(visit.submittedAt) }}</span>
          </div>
          <div>
            <StatusTag kind="visit" :value="visit.status" />
            <StatusTag v-if="triage?.riskLevel" kind="risk" :value="triage.riskLevel" style="margin-left:8px" />
          </div>
        </div>
        <el-alert type="warning" :title="DISCLAIMER" show-icon :closable="false" class="mt-12" />
      </div>

      <el-row :gutter="14">
        <!-- 左：原始信息 -->
        <el-col :span="8">
          <div class="page-card">
            <h4 class="page-title">原始问诊信息</h4>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="主诉">{{ visit.formData.chiefComplaint }}</el-descriptions-item>
              <el-descriptions-item label="起病时间">{{ fmtDate(visit.formData.onsetTime) }}</el-descriptions-item>
              <el-descriptions-item label="持续/程度">{{ visit.formData.duration || '-' }} / {{ visit.formData.severity ? severityMap[visit.formData.severity] : '-' }}</el-descriptions-item>
              <el-descriptions-item label="伴随症状">{{ visit.formData.accompanying?.join('、') || '无' }}</el-descriptions-item>
              <el-descriptions-item label="诱因">{{ visit.formData.triggers || '-' }}</el-descriptions-item>
              <el-descriptions-item label="既往史">{{ visit.formData.pastHistory || '无' }}</el-descriptions-item>
              <el-descriptions-item label="过敏史">{{ visit.formData.allergyHistory || '无' }}</el-descriptions-item>
              <el-descriptions-item label="用药">{{ visit.formData.medication || '无' }}</el-descriptions-item>
              <el-descriptions-item label="特殊人群">{{ specialGroupMap[visit.formData.basic?.specialGroup || 'NONE'] }}</el-descriptions-item>
              <el-descriptions-item label="补充">{{ visit.formData.supplement || '-' }}</el-descriptions-item>
            </el-descriptions>
          </div>
          <div class="page-card">
            <h4 class="page-title">结构化结果
              <span class="muted" v-if="triage?.extraction">（置信度 {{ triage.extraction.confidence ?? '-' }}）</span>
            </h4>
            <SymptomTable :symptoms="triage?.extraction?.symptoms || []" />
            <div v-if="triage?.extraction?.missingFields?.length" class="mt-12">
              <el-tag v-for="m in triage.extraction.missingFields" :key="m" type="warning" size="small" style="margin:0 6px 6px 0">缺失：{{ m }}</el-tag>
            </div>
          </div>
        </el-col>

        <!-- 中：规则 + 引用 -->
        <el-col :span="8">
          <div class="page-card">
            <h4 class="page-title">规则引擎命中（确定性安全底线）</h4>
            <el-empty v-if="!ruleHits.length" description="未命中规则" :image-size="60" />
            <div v-for="h in ruleHits" :key="h.id" class="rule-hit">
              <div class="flex-between">
                <b>{{ h.ruleName }} <span class="muted mono">{{ h.ruleCode }} v{{ h.ruleVersion }}</span></b>
                <StatusTag kind="risk" :value="h.riskLevel" />
              </div>
              <div class="mt-4" style="font-size:13px">{{ h.message }}</div>
            </div>
          </div>
          <div class="page-card">
            <h4 class="page-title">指南引用（可追溯）</h4>
            <CitationList :citations="citations" />
          </div>
        </el-col>

        <!-- 右：AI 摘要 + 安全 + 审核操作 -->
        <el-col :span="8">
          <div class="page-card">
            <h4 class="page-title">AI 辅助摘要（经安全审查）</h4>
            <template v-if="triage?.summaryForReview">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="风险摘要">{{ triage.riskSummary || '-' }}</el-descriptions-item>
              </el-descriptions>
              <div v-for="(rp, i) in triage.summaryForReview.riskPoints || []" :key="i" class="risk-point">
                <StatusTag kind="risk" :value="rp.severity || 'LOW'" />
                <span style="margin-left:6px">{{ rp.point }}</span>
                <div class="muted mt-4">{{ rp.basis }}</div>
              </div>
              <div v-if="triage.summaryForReview.suggestedFocus?.length" class="mt-12">
                <b style="font-size:13px">建议关注：</b>
                <el-tag v-for="s in triage.summaryForReview.suggestedFocus" :key="s" size="small" style="margin:4px 6px 0 0">{{ s }}</el-tag>
              </div>
            </template>
            <el-empty v-else description="暂无 AI 摘要" :image-size="60" />
          </div>

          <div class="page-card">
            <h4 class="page-title">安全审查结果</h4>
            <el-tag :type="triage?.safetyStatus === 'PASS' ? 'success' : 'danger'">
              {{ triage?.safetyStatus === 'PASS' ? 'PASS（已通过安全审查）' : (triage?.safetyStatus || '未知') }}
            </el-tag>
            <div v-if="triage?.safetyIssues?.length" class="mt-12">
              <div v-for="(iss, i) in triage.safetyIssues" :key="i" class="muted" style="font-size:12px">• {{ iss.type }}：{{ iss.detail }}</div>
            </div>
            <div class="muted mt-12" style="font-size:12px">
              版本：模型 {{ triage?.modelName || '-' }} · Prompt {{ triage?.promptVersion || '-' }} · 知识库 {{ triage?.knowledgeVersion || '-' }} · 规则 {{ triage?.ruleVersion || '-' }}
            </div>
          </div>

          <div class="page-card" v-if="canReview">
            <h4 class="page-title">人工审核</h4>
            <el-form label-width="90px">
              <el-form-item label="风险等级">
                <el-select v-model="reviewForm.modifiedRiskLevel" class="w-100">
                  <el-option v-for="(v, k) in riskMap" :key="k" :label="v.label" :value="k" />
                </el-select>
              </el-form-item>
              <el-form-item label="审核意见">
                <el-input v-model="reviewForm.comment" type="textarea" :rows="3" placeholder="填写审核意见（必填）" />
              </el-form-item>
              <el-form-item label="修改建议">
                <el-input v-model="reviewForm.modifiedText" type="textarea" :rows="3" placeholder="可修改 AI 建议内容（可选）" />
              </el-form-item>
              <el-form-item>
                <el-button type="success" :loading="acting" @click="act('APPROVE')">通 过</el-button>
                <el-button type="warning" :loading="acting" @click="act('REQUEST_INFO')">要求补充</el-button>
                <el-button type="danger" :loading="acting" @click="act('REJECT')">驳 回</el-button>
              </el-form-item>
            </el-form>
          </div>

          <div class="page-card" v-if="reviews.length">
            <h4 class="page-title">审核历史</h4>
            <el-timeline>
              <el-timeline-item v-for="r in reviews" :key="r.id" :timestamp="fmtTime(r.createdAt)">
                <b>{{ r.reviewerName }}</b> {{ reviewActionMap[r.action] }}
                <div class="muted">{{ r.comment }}</div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { reviewApi, triageApi, visitApi } from '@/api/modules'
import { StatusTag } from '@/components/Tags'
import SymptomTable from '@/components/SymptomTable.vue'
import CitationList from '@/components/CitationList.vue'
import type { Citation, ReviewAction, ReviewRecord, RiskLevel, RuleHit, TriageDetail, Visit } from '@/types'
import { fmtDate, fmtTime, reviewActionMap, riskMap, severityMap, specialGroupMap, DISCLAIMER } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const visitId = Number(route.params.id)
const loading = ref(true)
const acting = ref(false)
const visit = ref<Visit | null>(null)
const triage = ref<TriageDetail | null>(null)
const ruleHits = ref<RuleHit[]>([])
const citations = ref<Citation[]>([])
const reviews = ref<ReviewRecord[]>([])

const reviewForm = reactive<{ comment: string; modifiedRiskLevel: RiskLevel; modifiedText: string }>({
  comment: '',
  modifiedRiskLevel: 'LOW',
  modifiedText: ''
})

const canReview = computed(() => visit.value && ['PENDING_REVIEW', 'NEED_INFO'].includes(visit.value.status))

async function load() {
  loading.value = true
  try {
    const [v, tg, rh, ci, rv] = await Promise.all([
      visitApi.detail(visitId),
      triageApi.detail(visitId).catch(() => ({ data: null })),
      triageApi.ruleHits(visitId).catch(() => ({ data: [] })),
      triageApi.citations(visitId).catch(() => ({ data: [] })),
      reviewApi.history(visitId).catch(() => ({ data: [] }))
    ])
    visit.value = v.data
    triage.value = tg.data
    ruleHits.value = rh.data
    citations.value = ci.data
    reviews.value = rv.data
    reviewForm.modifiedRiskLevel = tg.data?.riskLevel || 'LOW'
  } finally { loading.value = false }
}

async function act(action: ReviewAction) {
  if (!reviewForm.comment.trim()) return ElMessage.warning('请填写审核意见')
  const label = reviewActionMap[action]
  await ElMessageBox.confirm(`确认「${label}」该问诊记录？`, '审核确认', { type: 'warning' })
  acting.value = true
  try {
    await reviewApi.submit({
      visitId,
      action,
      comment: reviewForm.comment,
      modifiedRiskLevel: reviewForm.modifiedRiskLevel,
      modifiedSummary: reviewForm.modifiedText
        ? { ...(triage.value?.summaryForReview || {}), riskSummary: reviewForm.modifiedText }
        : undefined
    })
    ElMessage.success(`已${label}`)
    if (action === 'APPROVE') {
      const go = await ElMessageBox.confirm('审核已通过。是否立即为该患者创建随访计划？', '后续操作', {
        confirmButtonText: '创建随访计划', cancelButtonText: '返回工作台', type: 'success'
      }).catch(() => false)
      if (go) router.push(`/d/followup-plan-new/${visitId}`)
      else router.push('/d/workbench')
    } else {
      router.push('/d/workbench')
    }
  } finally { acting.value = false }
}

onMounted(load)
</script>

<style scoped>
.rule-hit { border-left: 3px solid #f56c6c; padding: 6px 10px; margin-bottom: 10px; background: #fef0f0; border-radius: 0 4px 4px 0; }
.risk-point { border-left: 3px solid #e6a23c; padding: 4px 10px; margin-top: 8px; background: #fdf6ec; }
.mt-4 { margin-top: 4px; }
</style>
