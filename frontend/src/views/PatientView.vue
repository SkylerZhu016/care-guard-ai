<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, CircleCheck, Delete, DocumentAdd, Plus, WarningFilled } from '@element-plus/icons-vue'
import { ApiClient } from '../api'
import { formatDate, isSupportedSymptom, reasonLabel, SYMPTOM_BY_CODE, SYMPTOM_CATALOG } from '../domain/presentation'
import { useSessionStore } from '../stores/session'
import type { Symptom, Task, Visit } from '../types'
import StatusPill from '../components/StatusPill.vue'

const session=useSessionStore(),api=new ApiClient(()=>session.token)
const visits=ref<Visit[]>([]),tasks=ref<Task[]>([]),saving=ref(false),draftId=ref<string|null>(null),currentStep=ref(1),pendingCode=ref(''),activityTab=ref<'VISITS'|'TASKS'>('VISITS'),activityPage=ref(1)
const pageSize=5
const form=reactive({chiefComplaint:'',freeText:'',symptoms:[] as Symptom[]})
const allSymptomsSupported=computed(()=>form.symptoms.length>0&&form.symptoms.every(s=>isSupportedSymptom(s.code)))
const canSave=computed(()=>!!form.chiefComplaint.trim()&&allSymptomsSupported.value)
const pagedVisits=computed(()=>visits.value.slice((activityPage.value-1)*pageSize,activityPage.value*pageSize))
const pagedTasks=computed(()=>tasks.value.slice((activityPage.value-1)*pageSize,activityPage.value*pageSize))
const activityTotal=computed(()=>activityTab.value==='VISITS'?visits.value.length:tasks.value.length)

async function load(){[visits.value,tasks.value]=await Promise.all([api.myVisits(),api.myTasks()])}
function addSymptom(){if(!pendingCode.value)return;const option=SYMPTOM_BY_CODE.get(pendingCode.value);if(!option)return;if(form.symptoms.some(s=>s.code===option.code)){ElMessage.info('该症状已经添加');return}form.symptoms.push({code:option.code,name:option.name,severity:3,onset:''});pendingCode.value=''}
function removeSymptom(index:number){form.symptoms.splice(index,1)}
function loadTeachingExample(){draftId.value=null;form.chiefComplaint='合成胸痛伴呼吸困难';form.freeText='仅为工程教学模拟，无真实个人信息。';form.symptoms.splice(0,form.symptoms.length,{code:'CHEST_PAIN',name:'胸痛',severity:8,onset:'1 小时前'},{code:'DYSPNEA',name:'呼吸困难',severity:6,onset:'30 分钟前'});currentStep.value=1;ElMessage.success('已加载红旗教学示例，可继续修改')}
function editDraft(visit:Visit){draftId.value=visit.id;form.chiefComplaint=visit.chiefComplaint;form.freeText=visit.freeText;form.symptoms.splice(0,form.symptoms.length,...visit.symptoms.map(s=>({...s})));currentStep.value=1;if(!form.symptoms.every(s=>isSupportedSymptom(s.code)))ElMessage.warning('该历史草稿含未支持症状；删除或替换后才能再次提交');window.scrollTo({top:0,behavior:'smooth'})}
function resetForm(){draftId.value=null;form.chiefComplaint='';form.freeText='';form.symptoms.splice(0);pendingCode.value='';currentStep.value=1}
function nextStep(){if(currentStep.value===1&&!form.chiefComplaint.trim()){ElMessage.warning('请先用合成信息描述主要不适');return}if(currentStep.value===2&&!allSymptomsSupported.value){ElMessage.warning('请至少选择一项当前已支持的症状');return}currentStep.value=Math.min(3,currentStep.value+1)}
async function saveDraft(showSuccess=true){if(!canSave.value){ElMessage.warning('请完成主要不适和受支持症状后再保存');return null}saving.value=true;try{const saved=draftId.value?await api.updateVisit(draftId.value,form):await api.createVisit(form);draftId.value=saved.id;if(showSuccess)ElMessage.success('完整草稿已保存');await load();return saved}catch(e:any){ElMessage.error(e.message);return null}finally{saving.value=false}}
async function submitDraft(){const saved=await saveDraft(false);if(!saved)return;try{await ElMessageBox.confirm('提交后草稿不可再编辑。系统只按页面列出的有限规则执行筛查，结果仍必须人工审核。','确认提交教学病例',{type:'warning',confirmButtonText:'确认提交',cancelButtonText:'返回检查'});await api.submitVisit(saved.id);ElMessage.success('已提交并进入人工审核队列');resetForm();activityTab.value='VISITS';activityPage.value=1;await load()}catch(e:any){if(e!=='cancel'&&e!=='close')ElMessage.error(e.message)}}
watch(activityTab,()=>activityPage.value=1)
onMounted(()=>load().catch((e:any)=>ElMessage.error(e.message)))
</script>

<template>
  <section class="workspace patient-workspace">
    <div class="page-heading"><div><span class="eyebrow">PATIENT SIMULATION</span><h1>创建受控教学病例</h1><p>通过中文选择受支持症状；内部代码由系统维护，所有结果进入人工审核。</p></div><button class="example-button" type="button" @click="loadTeachingExample"><DocumentAdd/>加载红旗教学示例</button></div>
    <div class="coverage-banner" role="note"><WarningFilled/><div><strong>当前仅支持 4 类结构化症状</strong><p>胸痛、呼吸困难、晕厥和意识异常。没有出现在列表中的症状，不代表系统判断其安全。</p></div></div>
    <div class="patient-layout">
      <article class="panel intake-panel">
        <nav class="form-stepper" aria-label="预问诊填写步骤">
          <button v-for="(label,index) in ['主要不适','选择症状','确认提交']" :key="label" type="button" :class="{active:currentStep===index+1,done:currentStep>index+1}" :aria-current="currentStep===index+1?'step':undefined" @click="currentStep=index+1"><span>{{index+1}}</span>{{label}}</button>
        </nav>
        <el-form label-position="top" class="intake-form" @submit.prevent>
          <section v-show="currentStep===1" aria-labelledby="step-one-title"><div class="section-heading"><div><span class="step-kicker">步骤 1 / 3</span><h2 id="step-one-title">先描述主要不适</h2><p>只填写虚构的成人教学信息，不要输入任何真实身份或病历资料。</p></div><span class="safe-chip">写库前自动脱敏</span></div><el-form-item label="主要不适（合成描述）" required><el-input v-model="form.chiefComplaint" maxlength="500" show-word-limit placeholder="例如：合成胸痛伴呼吸困难" /></el-form-item><el-form-item label="补充说明（可选）"><el-input v-model="form.freeText" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="可补充虚构的发生场景；不要填写真实个人信息" /></el-form-item></section>
          <section v-show="currentStep===2" aria-labelledby="step-two-title"><div class="section-heading"><div><span class="step-kicker">步骤 2 / 3</span><h2 id="step-two-title">从受支持目录选择症状</h2><p>患者无需填写英文代码。每项症状都会显示当前规则覆盖状态。</p></div></div><div class="symptom-picker"><el-select v-model="pendingCode" filterable placeholder="按中文名称搜索症状" aria-label="搜索受支持症状"><el-option v-for="item in SYMPTOM_CATALOG" :key="item.code" :label="`${item.name} · ${item.category}`" :value="item.code" :disabled="form.symptoms.some(s=>s.code===item.code)"/></el-select><el-button type="primary" :icon="Plus" :disabled="!pendingCode" @click="addSymptom">添加症状</el-button></div><div v-if="!form.symptoms.length" class="empty-state compact-empty"><DocumentAdd/><strong>尚未选择症状</strong><p>请从上方目录至少添加一项当前支持的症状。</p></div><div class="selected-symptoms" aria-live="polite"><article v-for="(s,index) in form.symptoms" :key="`${s.code}-${index}`" class="symptom-card" :class="{unsupported:!isSupportedSymptom(s.code)}"><div class="symptom-card-head"><div><strong>{{SYMPTOM_BY_CODE.get(s.code)?.name||s.name}}</strong><small>{{s.code}} · {{SYMPTOM_BY_CODE.get(s.code)?.coverageLabel||'未配置自动规则'}}</small></div><el-button circle plain type="danger" :icon="Delete" :aria-label="`删除${s.name||s.code}`" @click="removeSymptom(index)"/></div><p class="coverage-hint">{{SYMPTOM_BY_CODE.get(s.code)?.hint||'该历史症状不在当前支持目录中，请删除后再提交。'}}</p><div class="symptom-details"><el-form-item label="起病时间（可选）"><el-input v-model="s.onset" placeholder="例如：1 小时前"/></el-form-item><div class="severity-control"><div><label :for="`severity-${index}`">主观严重程度</label><strong>{{s.severity}} / 10</strong></div><el-slider :id="`severity-${index}`" v-model="s.severity" :min="0" :max="10" show-stops /></div></div></article></div></section>
          <section v-show="currentStep===3" aria-labelledby="step-three-title"><div class="section-heading"><div><span class="step-kicker">步骤 3 / 3</span><h2 id="step-three-title">确认后再提交</h2><p>提交不会产生诊断；只会执行有限规则、受控 AI 整理并进入人工审核。</p></div></div><div class="review-summary"><div><span>主要不适</span><strong>{{form.chiefComplaint||'尚未填写'}}</strong></div><div><span>结构化症状</span><strong>{{form.symptoms.map(s=>SYMPTOM_BY_CODE.get(s.code)?.name||s.name).join('、')||'尚未选择'}}</strong></div><div><span>规则覆盖</span><strong :class="allSymptomsSupported?'positive':'negative'">{{allSymptomsSupported?'所选症状均在当前目录':'存在未支持症状，不能提交'}}</strong></div></div><div class="consent"><CircleCheck/><span>我确认以上均为虚构的成人教学信息，不包含真实个人或医疗数据。</span></div></section>
          <div class="form-actions"><el-button v-if="currentStep>1" :icon="ArrowLeft" @click="currentStep--">上一步</el-button><div class="action-spacer"/><el-button :loading="saving" :disabled="!canSave" @click="saveDraft(true)">保存完整草稿</el-button><el-button v-if="currentStep<3" type="primary" @click="nextStep">下一步<el-icon class="el-icon--right"><ArrowRight/></el-icon></el-button><el-button v-else type="primary" size="large" :loading="saving" :disabled="!canSave" @click="submitDraft">提交并进入人工审核</el-button><el-button v-if="draftId" text @click="resetForm">放弃当前编辑</el-button></div>
        </el-form>
      </article>
      <aside class="panel activity-panel">
        <div class="activity-header"><div><span class="step-kicker">个人进度</span><h2>病例与随访</h2></div><span class="activity-total">{{activityTotal}}</span></div>
        <div class="segmented-control" role="tablist" aria-label="个人进度类型"><button type="button" role="tab" :aria-selected="activityTab==='VISITS'" :class="{active:activityTab==='VISITS'}" @click="activityTab='VISITS'">病例进度 <span>{{visits.length}}</span></button><button type="button" role="tab" :aria-selected="activityTab==='TASKS'" :class="{active:activityTab==='TASKS'}" @click="activityTab='TASKS'">随访任务 <span>{{tasks.length}}</span></button></div>
        <div v-if="activityTab==='VISITS'" role="tabpanel"><div v-if="!visits.length" class="empty-state"><DocumentAdd/><strong>还没有教学病例</strong><p>完成左侧表单后，状态会显示在这里。</p></div><article v-for="v in pagedVisits" :key="v.id" class="case-card"><div class="case-head"><div><strong>{{v.chiefComplaint}}</strong><small>{{formatDate(v.createdAt)}} · {{v.symptoms.map(s=>SYMPTOM_BY_CODE.get(s.code)?.name||s.name).join('、')}}</small></div><StatusPill :value="v.status" /></div><div v-if="v.triage" class="case-result"><span>规则结果</span><StatusPill :value="v.triage.ruleUrgency" /></div><p v-if="v.triage" class="reason-copy">{{v.triage.ruleReasons.map(reasonLabel).join('；')}}</p><div v-if="v.runs[0]" class="run-line"><span>AI 辅助 · {{v.runs[0].provider}}</span><StatusPill :value="v.runs[0].status" /></div><el-button v-if="v.status==='DRAFT'" size="small" plain @click="editDraft(v)">继续编辑草稿</el-button></article></div>
        <div v-else role="tabpanel"><div v-if="!tasks.length" class="empty-state"><CircleCheck/><strong>暂无随访任务</strong><p>只有医务人员明确选择并激活适用模板后才会出现。</p></div><article v-for="task in pagedTasks" :key="task.id" class="task-row"><div><strong>{{task.title}}</strong><small>截止 {{formatDate(task.dueAt)}}</small></div><StatusPill :value="task.status" /></article></div>
        <el-pagination v-if="activityTotal>pageSize" v-model:current-page="activityPage" :page-size="pageSize" :total="activityTotal" layout="prev, pager, next" class="pagination"/>
      </aside>
    </div>
  </section>
</template>
