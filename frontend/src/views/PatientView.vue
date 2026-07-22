<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ApiClient } from '../api'
import { useSessionStore } from '../stores/session'
import type { Symptom, Task, Visit } from '../types'
import StatusPill from '../components/StatusPill.vue'

const session=useSessionStore(), api=new ApiClient(()=>session.token)
const visits=ref<Visit[]>([]), tasks=ref<Task[]>([]), saving=ref(false), draftId=ref<string|null>(null)
const blankSymptom=():Symptom=>({code:'HEADACHE',name:'头痛',severity:3,onset:''})
const form=reactive({chiefComplaint:'合成胸痛伴呼吸困难',freeText:'仅为工程教学模拟，无真实个人信息。',symptoms:[{code:'CHEST_PAIN',name:'胸痛',severity:8,onset:'1 小时前'},{code:'DYSPNEA',name:'呼吸困难',severity:6,onset:'30 分钟前'}] as Symptom[]})

async function load(){[visits.value,tasks.value]=await Promise.all([api.myVisits(),api.myTasks()])}
function addSymptom(){form.symptoms.push(blankSymptom())}
function removeSymptom(index:number){if(form.symptoms.length===1){ElMessage.warning('至少保留一项结构化症状');return}form.symptoms.splice(index,1)}
function editDraft(visit:Visit){draftId.value=visit.id;form.chiefComplaint=visit.chiefComplaint;form.freeText=visit.freeText;form.symptoms.splice(0,form.symptoms.length,...visit.symptoms.map(s=>({...s})));window.scrollTo({top:0,behavior:'smooth'})}
function resetForm(){draftId.value=null;form.chiefComplaint='';form.freeText='';form.symptoms.splice(0,form.symptoms.length,blankSymptom())}
async function saveDraft(){saving.value=true;try{const saved=draftId.value?await api.updateVisit(draftId.value,form):await api.createVisit(form);draftId.value=saved.id;ElMessage.success('草稿已保存，可继续编辑或提交');await load();return saved}catch(e:any){ElMessage.error(e.message);return null}finally{saving.value=false}}
async function submitDraft(){const saved=await saveDraft();if(!saved)return;try{await ElMessageBox.confirm('提交后草稿不可再编辑，并会立即执行安全筛查。','确认提交',{type:'warning'});await api.submitVisit(saved.id);ElMessage.success('已进入人工审核队列');resetForm();await load()}catch(e:any){if(e!=='cancel'&&e!=='close')ElMessage.error(e.message)}}
onMounted(()=>load().catch((e:any)=>ElMessage.error(e.message)))
</script>

<template><section class="workspace"><div class="page-heading"><div><span class="eyebrow">PATIENT SIMULATION</span><h1>预问诊与我的随访</h1><p>只填写合成教学信息。可先保存草稿，确认后再提交安全筛查。</p></div><div class="stat-card"><strong>{{visits.length}}</strong><span>历史教学病例</span></div></div>
  <div class="two-column"><article class="panel"><div class="panel-title"><div><span class="step">01</span><h2>{{draftId?'编辑合成草稿':'创建合成预问诊'}}</h2></div><span class="safe-chip">写库前自动脱敏</span></div>
    <el-form label-position="top"><el-form-item label="主要不适（合成描述）"><el-input v-model="form.chiefComplaint" maxlength="500" show-word-limit /></el-form-item>
      <div class="symptom-list"><div v-for="(s,index) in form.symptoms" :key="index" class="symptom-editor"><div class="symptom-fields"><el-input v-model="s.code" placeholder="代码，如 HEADACHE"/><el-input v-model="s.name" placeholder="症状名称"/><el-input v-model="s.onset" placeholder="起病时间（可选）"/></div><div class="severity-row"><span>程度 {{s.severity}} / 10</span><el-slider v-model="s.severity" :min="0" :max="10"/><el-button type="danger" plain @click="removeSymptom(index)">删除</el-button></div></div></div>
      <el-button plain @click="addSymptom">＋ 添加症状</el-button><el-form-item label="补充说明" class="top-gap"><el-input v-model="form.freeText" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item><div class="consent">✓ 我确认以上为虚构的成人教学病例，不包含真实个人或医疗数据。</div><div class="button-row"><el-button size="large" :loading="saving" @click="saveDraft">保存草稿</el-button><el-button type="primary" size="large" :loading="saving" @click="submitDraft">提交并执行安全筛查</el-button><el-button v-if="draftId" text @click="resetForm">新建草稿</el-button></div></el-form>
  </article><aside class="panel"><div class="panel-title"><div><span class="step">02</span><h2>处理进度</h2></div></div><div v-if="!visits.length" class="empty">尚无病例，保存后可在此查看。</div><div v-for="v in visits" :key="v.id" class="case-card"><div class="case-head"><strong>{{v.chiefComplaint}}</strong><StatusPill :value="v.status" /></div><div class="risk-line" v-if="v.triage"><span>规则等级</span><StatusPill :value="v.triage.ruleUrgency" /></div><p v-if="v.triage">依据：{{v.triage.ruleReasons.join('、')}}</p><div v-if="v.runs[0]" class="run-line"><span>AI {{v.runs[0].provider}}</span><StatusPill :value="v.runs[0].status" /></div><el-button v-if="v.status==='DRAFT'" size="small" plain @click="editDraft(v)">继续编辑</el-button></div>
    <h3 class="section-label">我的随访任务</h3><div v-if="!tasks.length" class="empty compact">审核并激活计划后显示任务。</div><div v-for="task in tasks" :key="task.id" class="task-row"><div><strong>{{task.title}}</strong><small>{{new Date(task.dueAt).toLocaleDateString()}}</small></div><StatusPill :value="task.status" /></div></aside></div></section></template>
