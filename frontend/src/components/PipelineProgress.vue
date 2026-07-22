<template>
  <div>
    <div class="flex-between mb-12">
      <span>
        当前状态：<StatusTag kind="run" :value="run?.status || 'PENDING'" />
        <span class="muted" v-if="run?.currentStep">（{{ stepNameMap[run.currentStep] || run.currentStep }}）</span>
      </span>
      <span class="muted">{{ viaSse ? 'SSE 实时推送' : '轮询模式' }}</span>
    </div>
    <el-steps :active="activeIndex" align-center finish-status="success" :process-status="run?.status === 'FAILED' || run?.status === 'REVIEW_FAILED' ? 'error' : 'process'">
      <el-step v-for="s in steps" :key="s" :title="stepNameMap[s]"
        :status="stepStatus(s)" />
    </el-steps>
    <el-alert v-if="run?.status === 'FAILED'" type="error" class="mt-12" :title="'处理失败：' + (run.errorMessage || '未知错误')" show-icon :closable="false" />
    <el-alert v-if="run?.status === 'REVIEW_FAILED'" type="warning" class="mt-12" title="AI 输出未通过安全审查，已转入人工处理队列" show-icon :closable="false" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { agentRunApi, visitApi } from '@/api/modules'
import { tokenStore } from '@/api/http'
import { stepNameMap } from '@/utils/format'
import { StatusTag } from '@/components/Tags'
import type { AgentRun } from '@/types'

const props = defineProps<{ visitId: number }>()
const emit = defineEmits<{ (e: 'finished', run: AgentRun): void }>()

const steps = ['STRUCTURE', 'RETRIEVE', 'RISK', 'SAFETY', 'SUMMARY']
const run = ref<AgentRun | null>(null)
const viaSse = ref(true)
const activeIndex = ref(0)
let es: EventSource | null = null
let timer: number | null = null
let done = false

function stepStatus(s: string) {
  const r = run.value
  if (!r) return 'wait'
  const step = r.steps?.find((x) => x.step === s)
  if (step?.status === 'SUCCESS') return 'success'
  if (step?.status === 'FAILED') return 'error'
  if (step?.status === 'RUNNING') return 'process'
  return 'wait'
}

function applyRun(r: AgentRun) {
  run.value = r
  const idx = steps.findIndex((s) => s === r.currentStep)
  const successCount = (r.steps || []).filter((x) => x.status === 'SUCCESS').length
  activeIndex.value = r.status === 'COMPLETED' ? steps.length : Math.max(idx, successCount)
  if (['COMPLETED', 'FAILED', 'REVIEW_FAILED', 'TIMEOUT', 'CANCELLED', 'PARTIAL'].includes(r.status)) {
    finish()
    emit('finished', r)
  }
}

function finish() {
  done = true
  es?.close(); es = null
  if (timer) { window.clearInterval(timer); timer = null }
}

async function loadRun() {
  try {
    const { data } = await visitApi.agentRun(props.visitId)
    if (data) applyRun(data)
  } catch { /* ignore */ }
}

function startPolling() {
  if (timer || done) return
  viaSse.value = false
  timer = window.setInterval(loadRun, 3000)
}

function startSse(runId: number) {
  try {
    es = new EventSource(agentRunApi.sseUrl(runId, tokenStore.access))
    es.onmessage = (ev) => { if (!done) loadRun() }
    es.onerror = () => { es?.close(); es = null; startPolling() }
  } catch {
    startPolling()
  }
}

onMounted(async () => {
  await loadRun()
  if (!done && run.value?.id) startSse(run.value.id)
  else if (!done) startPolling()
})

watch(() => props.visitId, () => { done = false; loadRun() })
onBeforeUnmount(finish)
</script>
