<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRight, CircleCheck, Clock, Document, EditPen, Link, Search, WarningFilled } from '@element-plus/icons-vue'
import { reviewCases } from '../mock'
import type { Tone } from '../types'
import AppModal from '../components/AppModal.vue'
import StatusPill from '../components/StatusPill.vue'

const selectedId = ref(reviewCases[0].id)
const tab = ref<'facts' | 'evidence' | 'plan'>('facts')
const query = ref('')
const decision = ref('确认优先级并创建随访')
const showDecision = ref(false)
const showPriority = ref(false)
const toast = ref('')
const manualPriority = ref('优先')
const priorityReason = ref('')
const selectedCase = computed(() => reviewCases.find((item) => item.id === selectedId.value)!)
const filteredCases = computed(() => reviewCases.filter((item) => `${item.name}${item.complaint}${item.id}`.includes(query.value.trim())))
const priorityOptions = [
  { value: '优先', description: '需要优先处理或尽快线下评估' },
  { value: '常规', description: '按常规审核顺序继续处理' },
  { value: '需补充', description: '信息不足，先联系患者补充' },
  { value: '暂缓', description: '暂不进入后续处理流程' },
]
const manualPriorityTone = computed<Tone>(() => manualPriority.value === '优先' ? 'red' : manualPriority.value === '常规' ? 'green' : manualPriority.value === '需补充' ? 'amber' : 'gray')

watch(selectedId, () => {
  manualPriority.value = selectedCase.value.level
  priorityReason.value = ''
})

function completeReview() {
  toast.value = `${selectedCase.value.id} 已完成人工终审`
  showDecision.value = false
  window.setTimeout(() => { toast.value = '' }, 2600)
}
function savePriority() {
  toast.value = `${selectedCase.value.id} 的人工优先级已调整为“${manualPriority.value}”`
  showPriority.value = false
  window.setTimeout(() => { toast.value = '' }, 2600)
}
</script>

<template>
  <section class="page-stack">
    <Transition name="toast"><div v-if="toast" class="toast"><CircleCheck />{{ toast }}</div></Transition>
    <header class="page-header">
      <div><span class="eyebrow">医务端 · 人工终审</span><h1>待审核问诊</h1><p>患者原话、事实字段、规则结论和证据来源分层呈现，便于快速核对。</p></div>
      <div class="header-stat"><span>当前待审核</span><strong>12</strong><small>其中 3 条需优先处理</small></div>
    </header>

    <div class="workflow-line"><span class="done"><CircleCheck />患者提交</span><i /><span class="done"><CircleCheck />规则整理</span><i /><span class="active"><Clock />人工审核</span><i /><span><Document />随访决策</span></div>

    <div class="review-layout">
      <aside class="surface review-queue">
        <div class="queue-head"><div><span class="eyebrow">审核队列</span><h2>优先处理</h2></div><StatusPill label="12 条" tone="red" /></div>
        <label class="compact-search"><Search /><input v-model="query" type="search" placeholder="搜索编号或主诉" /></label>
        <div class="queue-filter"><button class="active" type="button">全部</button><button type="button">优先</button><button type="button">待补充</button></div>
        <div class="queue-list">
          <button v-for="item in filteredCases" :key="item.id" type="button" :class="{ active: selectedId === item.id }" @click="selectedId = item.id">
            <span class="queue-time">{{ item.time }}</span><div><small>{{ item.id }}</small><strong>{{ item.complaint }}</strong><span>{{ item.name }} · {{ item.status }}</span></div><StatusPill :label="item.level" :tone="item.tone" /><ArrowRight />
          </button>
        </div>
        <footer><button type="button">上一页</button><span>1 / 3</span><button type="button">下一页</button></footer>
      </aside>

      <article class="surface review-detail">
        <header class="detail-head"><div><span class="case-code">{{ selectedCase.id }}</span><h2>{{ selectedCase.name }}</h2><p>提交于 2026-07-22 14:28 · 信息已脱敏</p></div><div class="detail-head-actions"><StatusPill :label="`人工优先级：${manualPriority}`" :tone="manualPriorityTone" dot /><button class="button priority-button" type="button" @click="showPriority = true"><EditPen />调整优先级</button><button class="button secondary" type="button"><EditPen />要求补充</button></div></header>

        <div class="summary-alert" :class="selectedCase.tone === 'red' ? 'danger' : ''"><WarningFilled /><div><strong>{{ selectedCase.tone === 'red' ? '系统建议：需要优先人工审核' : '系统建议：当前记录需要人工判断' }}</strong><p>{{ selectedCase.tone === 'red' ? '胸口不适与当前呼吸困难同时存在。医务人员可结合完整信息调整最终优先级。' : '自动规则未覆盖全部填写组合，请结合患者原话与事实字段审核并确定最终优先级。' }}</p></div><button type="button" @click="showPriority = true">人工调整</button></div>

        <nav class="detail-tabs" aria-label="审核内容">
          <button type="button" :class="{ active: tab === 'facts' }" @click="tab = 'facts'">患者事实 <b>6</b></button>
          <button type="button" :class="{ active: tab === 'evidence' }" @click="tab = 'evidence'">规则与证据 <b>3</b></button>
          <button type="button" :class="{ active: tab === 'plan' }" @click="tab = 'plan'">随访决策</button>
        </nav>

        <section v-if="tab === 'facts'" class="detail-section">
          <div class="section-head compact"><div><span class="step-label">患者原话</span><h3>{{ selectedCase.complaint }}</h3></div><StatusPill label="原始记录" tone="gray" /></div>
          <blockquote>“主要不适为胸口不适、呼吸困难。今天下午开始，走快一点时更明显，现在仍然存在。”</blockquote>
          <div class="fact-grid"><div><span>开始时间</span><strong>今天</strong><small>患者选择</small></div><div><span>持续情况</span><strong>间歇出现</strong><small>患者选择</small></div><div><span>当前状态</span><strong>仍然存在</strong><small>重点核对</small></div><div><span>活动影响</span><strong>需要停下休息</strong><small>重点核对</small></div></div>
          <div class="split-panels"><article><h4>健康资料快照</h4><dl><div><dt>年龄段</dt><dd>45–64 岁</dd></div><div><dt>慢性病</dt><dd>高血压</dd></div><div><dt>过敏史</dt><dd>未填写</dd></div></dl></article><article><h4>AI 信息整理</h4><p>患者报告胸口不适伴活动时呼吸困难，今天开始，当前仍存在。建议优先确认症状是否持续或加重。</p><small>AI 只做摘要，不改变规则结论</small></article></div>
        </section>

        <section v-else-if="tab === 'evidence'" class="detail-section">
          <div class="section-head compact"><div><span class="step-label">确定性规则</span><h3>命中 1 项已配置组合</h3></div><StatusPill label="red-flags-v1.4" tone="red" /></div>
          <div class="rule-card"><span class="rule-index">R1</span><div><strong>胸口不适 + 当前呼吸困难</strong><p>患者在症状特异追问中明确选择“同时存在呼吸困难”。</p><code>CHEST_PAIN_WITH_DYSPNEA</code></div><StatusPill label="优先" tone="red" /></div>
          <div class="evidence-list"><article><Link /><div><span>国家卫生健康委</span><strong>基层常见急症识别与转诊建议</strong><p>胸痛伴呼吸困难等表现需要及时进行线下评估。</p><button type="button">查看公开来源</button></div></article><article><Link /><div><span>系统规则说明</span><strong>红旗症状确定性规则说明</strong><p>该组合仅触发优先审核，不生成诊断或处方。</p><button type="button">查看规则版本</button></div></article></div>
        </section>

        <section v-else class="detail-section">
          <div class="section-head compact"><div><span class="step-label">终审结论</span><h3>选择审核结果与后续动作</h3></div></div>
          <div class="priority-review-card"><div><span>系统建议</span><StatusPill :label="selectedCase.level" :tone="selectedCase.tone" dot /></div><ArrowRight /><div><span>人工最终优先级</span><StatusPill :label="manualPriority" :tone="manualPriorityTone" dot /></div><button class="button secondary" type="button" @click="showPriority = true"><EditPen />修改</button></div>
          <div class="decision-options"><label><input v-model="decision" type="radio" value="确认优先级并创建随访" /><span><strong>确认人工优先级并创建随访</strong><small>采用当前人工优先级，并激活通用随访计划。</small></span></label><label><input v-model="decision" type="radio" value="确认优先级，不创建随访" /><span><strong>确认人工优先级，不创建随访</strong><small>保留人工审核结论，不创建后续任务。</small></span></label><label><input v-model="decision" type="radio" value="修改结论" /><span><strong>修改其他审核结论</strong><small>记录修改原因和新的人工判断。</small></span></label></div>
          <label class="form-field"><span>审核说明</span><textarea rows="4" value="已核对患者原话与事实字段，建议尽快线下评估，并创建症状变化复核任务。" /></label>
        </section>

        <footer class="review-actions"><span><strong>审核人：演示医务人员</strong><small>操作将写入不可变审计记录（Demo 中仅模拟）</small></span><button class="button secondary" type="button">暂存</button><button class="button primary" type="button" @click="showDecision = true">完成终审</button></footer>
      </article>
    </div>

    <AppModal :open="showPriority" eyebrow="人工终审" title="调整最终优先级" @close="showPriority = false">
      <div class="system-suggestion"><span>系统建议优先级</span><StatusPill :label="selectedCase.level" :tone="selectedCase.tone" dot /><p>系统建议仅作为审核参考，最终优先级由医务人员结合患者事实、规则证据和专业判断确定。</p></div>
      <fieldset class="priority-options"><legend>选择人工最终优先级</legend><button v-for="item in priorityOptions" :key="item.value" type="button" :class="{ selected: manualPriority === item.value }" @click="manualPriority = item.value"><span><strong>{{ item.value }}</strong><small>{{ item.description }}</small></span><CircleCheck v-if="manualPriority === item.value" /></button></fieldset>
      <label class="form-field"><span>调整原因 <b v-if="manualPriority !== selectedCase.level">必填</b></span><textarea v-model="priorityReason" rows="3" placeholder="请说明调整依据，便于后续审计和复核" /></label>
      <template #actions><button class="button secondary" type="button" @click="showPriority = false">取消</button><button class="button primary" type="button" :disabled="manualPriority !== selectedCase.level && !priorityReason.trim()" @click="savePriority">保存人工优先级</button></template>
    </AppModal>

    <AppModal :open="showDecision" eyebrow="人工终审" title="确认完成本次审核？" @close="showDecision = false">
      <div class="modal-summary"><span>审核对象</span><strong>{{ selectedCase.id }} · {{ selectedCase.complaint }}</strong><span>人工优先级</span><strong>{{ manualPriority }}</strong><span>审核结论</span><strong>{{ decision }}</strong></div>
      <p class="modal-copy">确认后将保存本次人工终审结论，并按所选方案进入后续流程。</p>
      <template #actions><button class="button secondary" type="button" @click="showDecision = false">返回检查</button><button class="button primary" type="button" @click="completeReview">确认完成</button></template>
    </AppModal>
  </section>
</template>
