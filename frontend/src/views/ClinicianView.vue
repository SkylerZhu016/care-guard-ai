<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, DocumentChecked, Files, WarningFilled } from '@element-plus/icons-vue'
import { ApiClient } from '../api'
import { formatDateTime, isHypertensionTeachingCase, reasonLabel, symptomName } from '../domain/presentation'
import { useSessionStore } from '../stores/session'
import type { ReviewDecision, Urgency, Visit } from '../types'
import StatusPill from '../components/StatusPill.vue'

type PlanChoice = 'NONE' | 'HYPERTENSION_TEACHING_V1'

const session=useSessionStore(),api=new ApiClient(()=>session.token)
const queue=ref<Visit[]>([]),selected=ref<Visit|null>(null),reviewed=ref<Visit|null>(null),busy=ref(false),planId=ref<string|null>(null)
const decision=ref<ReviewDecision>('ACCEPT'),finalUrgency=ref<Urgency>('ROUTINE'),reason=ref('已核对结构化信息、确定性规则与引用证据'),planChoice=ref<PlanChoice>('NONE')
const run=computed(()=>selected.value?.runs[0])
const hypertensionEligible=computed(()=>isHypertensionTeachingCase(reviewed.value||selected.value))

async function load(){queue.value=await api.queue();if(queue.value.length&&!selected.value)select(queue.value[0])}
function select(visit:Visit){selected.value=visit;reviewed.value=null;planId.value=null;planChoice.value='NONE';decision.value='ACCEPT';finalUrgency.value=visit.triage?.aiUrgency||visit.triage?.ruleUrgency||'ROUTINE'}
async function review(){
  if(!selected.value?.triage||!reason.value.trim())return ElMessage.warning('请填写人工审核理由')
  busy.value=true
  try{
    const result=await api.review(selected.value.triage.id,decision.value,reason.value.trim(),finalUrgency.value)
    reviewed.value=result;queue.value=queue.value.filter(v=>v.id!==result.id)
    if(decision.value==='REJECT'){ElMessage.success('病例已拒绝，不会创建随访计划');selected.value=null;reviewed.value=null;await load()}
    else ElMessage.success('人工终审已保存，请继续明确随访决策')
  }catch(e:any){ElMessage.error(e.message)}finally{busy.value=false}
}
async function confirmFollowup(){
  if(!reviewed.value)return
  if(planChoice.value==='NONE'){ElMessage.success('已确认本病例不创建随访计划');selected.value=null;reviewed.value=null;await load();return}
  if(!hypertensionEligible.value){ElMessage.error('该病例未标记为高血压教学场景，不能使用此模板');return}
  busy.value=true
  try{const plan=await api.createPlan(reviewed.value.id,planChoice.value);planId.value=plan.id;ElMessage.success('计划草稿已创建，激活后才会生成任务')}
  catch(e:any){ElMessage.error(e.message)}finally{busy.value=false}
}
async function activate(){
  if(!planId.value)return
  try{await ElMessageBox.confirm('激活后将生成并分配 4 个高血压教学任务。','确认激活计划',{type:'warning',confirmButtonText:'确认激活',cancelButtonText:'暂不激活'});busy.value=true;await api.activatePlan(planId.value);ElMessage.success('高血压教学随访计划已激活');selected.value=null;reviewed.value=null;planId.value=null;await load()}
  catch(e:any){if(e!=='cancel'&&e!=='close')ElMessage.error(e.message)}finally{busy.value=false}
}
onMounted(()=>load().catch((e:any)=>ElMessage.error(e.message)))
</script>

<template>
  <section class="workspace clinician-workspace">
    <div class="page-heading"><div><span class="eyebrow">CLINICAL REVIEW</span><h1>医务人员安全终审</h1><p>按病例信息、规则与 AI 证据、人工终审、随访决策四段完成处理。</p></div><div class="stat-card urgent"><strong>{{queue.length}}</strong><span>待人工审核</span></div></div>
    <div class="workflow-strip" aria-label="审核工作流"><span class="active">1 病例信息</span><span>2 规则与 AI 证据</span><span>3 人工终审</span><span>4 随访决策</span></div>
    <div class="review-layout">
      <aside class="queue panel"><div class="panel-title"><div><Files/><h2>待处理队列</h2></div><span class="safe-chip">按提交时间</span></div><div v-if="!queue.length" class="empty-state"><CircleCheck/><strong>当前队列已清空</strong><p>新的合成教学病例会显示在这里。</p></div><button v-for="visit in queue" :key="visit.id" class="queue-item" :class="{active:selected?.id===visit.id}" type="button" @click="select(visit)"><div><strong>{{visit.chiefComplaint}}</strong><small>{{formatDateTime(visit.submittedAt||visit.createdAt)}}</small></div><StatusPill :value="visit.triage?.ruleUrgency"/></button></aside>
      <article v-if="selected" class="panel detail">
        <div class="detail-header"><div><span class="eyebrow">CASE {{selected.id.slice(0,8)}}</span><h2>{{selected.chiefComplaint}}</h2></div><StatusPill :value="reviewed?.status||'PENDING_REVIEW'"/></div>
        <section class="review-section" aria-labelledby="case-info-title"><div class="review-section-title"><span>01</span><div><h3 id="case-info-title">病例信息</h3><p>核对合成主诉与结构化症状。</p></div></div><div class="fact-grid"><div v-for="symptom in selected.symptoms" :key="symptom.code" class="fact-card"><span>{{symptomName(symptom.code,symptom.name)}}</span><strong>{{symptom.severity}} / 10</strong><small>{{symptom.onset||'未填写起病时间'}} · {{symptom.code}}</small></div></div></section>
        <section class="review-section" aria-labelledby="evidence-title"><div class="review-section-title"><span>02</span><div><h3 id="evidence-title">规则与 AI 证据</h3><p>确定性规则是安全底线，AI 结果不能降低规则紧急度。</p></div></div><div class="evidence-grid"><div class="evidence-block risk"><div class="evidence-heading"><h4>确定性规则</h4><StatusPill :value="selected.triage?.ruleUrgency"/></div><ul><li v-for="code in selected.triage?.ruleReasons" :key="code">{{reasonLabel(code)}}</li></ul></div><div class="evidence-block"><div class="evidence-heading"><h4>AI 辅助整理</h4><StatusPill :value="run?.status"/></div><p>{{selected.triage?.aiSummary||'AI 不可用或被安全阻断；请依据规则结果人工处理。'}}</p><small>{{run?.provider||'未运行'}} / {{run?.model||'—'}}</small></div></div><details v-if="run?.citations.length" class="citation-drawer"><summary>查看 {{run.citations.length}} 条引用证据</summary><article v-for="citation in run.citations" :key="citation.chunkId" class="citation"><strong>{{citation.title}} · {{citation.section}}</strong><blockquote>{{citation.quote}}</blockquote><a :href="citation.sourceUrl" target="_blank" rel="noreferrer">核对公开来源</a><small>{{citation.licenseNote}}</small></article></details><div v-if="run?.safetyReasons.length" class="warning"><WarningFilled/>安全原因：{{run.safetyReasons.join('、')}}</div></section>
        <section class="review-section" aria-labelledby="decision-title"><div class="review-section-title"><span>03</span><div><h3 id="decision-title">人工终审</h3><p>系统会拒绝任何低于规则等级的最终结果。</p></div></div><div class="inline-fields three"><el-select v-model="decision" :disabled="!!reviewed" aria-label="审核决定"><el-option label="接受 AI 草案" value="ACCEPT"/><el-option label="人工修改" value="MODIFY"/><el-option label="拒绝病例" value="REJECT"/></el-select><el-select v-model="finalUrgency" :disabled="decision==='REJECT'||!!reviewed" aria-label="最终紧急度"><el-option label="常规 ROUTINE" value="ROUTINE"/><el-option label="紧急 URGENT" value="URGENT"/><el-option label="急症 EMERGENCY" value="EMERGENCY"/></el-select><el-input v-model="reason" :disabled="!!reviewed" maxlength="500" placeholder="人工审核理由（必填）"/></div><el-button v-if="!reviewed" type="primary" size="large" :loading="busy" :disabled="!reason.trim()" @click="review"><DocumentChecked/>保存人工终审</el-button><div v-else class="decision-banner"><CircleCheck/>审核已保存：{{reviewed.triage?.reviewDecision}}。系统没有自动创建随访。</div></section>
        <section v-if="reviewed" class="review-section followup-decision" aria-labelledby="followup-title"><div class="review-section-title"><span>04</span><div><h3 id="followup-title">明确随访决策</h3><p>默认不创建。只有适用的教学病例才能选择对应模板。</p></div></div><el-radio-group v-model="planChoice" class="plan-options" :disabled="!!planId"><el-radio value="NONE" border><strong>不创建随访</strong><small>本病例在人工终审后结束</small></el-radio><el-radio value="HYPERTENSION_TEACHING_V1" border :disabled="!hypertensionEligible"><strong>高血压教学模板 v1</strong><small>{{hypertensionEligible?'病例文本包含高血压教学标记':'当前病例不适用，不能选择'}}</small></el-radio></el-radio-group><div class="button-row"><el-button v-if="!planId" type="primary" :loading="busy" @click="confirmFollowup">确认随访决策</el-button><el-button v-else type="success" :loading="busy" @click="activate">激活计划并生成任务</el-button></div></section>
      </article>
      <article v-else class="panel empty-state large"><DocumentChecked/><strong>选择一个病例开始审核</strong><p>每个病例都必须经过人工终审；随访计划不会自动创建。</p></article>
    </div>
  </section>
</template>
