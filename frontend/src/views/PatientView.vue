<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, CircleCheck, Clock, Document, EditPen, FirstAidKit, FolderOpened, Plus, User } from '@element-plus/icons-vue'
import { ApiClient } from '../api'
import { formatDate, formatDateTime } from '../domain/presentation'
import { useSessionStore } from '../stores/session'
import type { CatalogSymptom, IntakeCatalog, PatientProfileInput, QuestionAnswer, SymptomReport, Task, Visit } from '../types'
import StatusPill from '../components/StatusPill.vue'

type Area = 'INTAKE'|'RECORDS'|'TASKS'|'PROFILE'
const session=useSessionStore(),api=new ApiClient(()=>session.token)
const area=ref<Area>('INTAKE'),step=ref(1),catalog=ref<IntakeCatalog>({version:'',symptoms:[]}),visits=ref<Visit[]>([]),tasks=ref<Task[]>([])
const loading=ref(true),saving=ref(false),draftId=ref<string|null>(null),query=ref(''),selectedCode=ref(''),customName=ref(''),supplementing=ref<string|null>(null)
const draftKey='patient-intake-v2-draft'
const profile=reactive<PatientProfileInput>({ageBand:'UNKNOWN',physiologicalInfoStatus:'UNKNOWN',physiologicalInfo:'',chronicConditionsStatus:'UNKNOWN',chronicConditions:[],allergiesStatus:'UNKNOWN',allergies:[],longTermMedicationsStatus:'UNKNOWN',longTermMedications:[]})
const form=reactive({primarySymptomCode:'',chiefComplaint:'',freeText:'',symptomReports:[] as SymptomReport[]})

const areas=[{id:'INTAKE',label:'新建预问诊',icon:FirstAidKit},{id:'RECORDS',label:'问诊记录',icon:FolderOpened},{id:'TASKS',label:'随访任务',icon:Bell},{id:'PROFILE',label:'健康资料',icon:User}] as const
const filteredCatalog=computed(()=>catalog.value.symptoms.filter(item=>!query.value.trim()||`${item.name}${item.category}`.includes(query.value.trim())))
const selectedDefinitions=computed(()=>form.symptomReports.map(report=>definition(report.symptomCode)).filter(Boolean) as CatalogSymptom[])
const canContinue=computed(()=>form.symptomReports.length>0&&!!form.chiefComplaint.trim())
const hasRecordOnly=computed(()=>selectedDefinitions.value.some(item=>item.supportLevel!=='RULE_SUPPORTED'))
const urgentSignal=computed(()=>{
  const yes=(id:string)=>form.symptomReports.some(report=>answer(report,id).selectedOptions.includes('YES'))
  return yes('consciousness.present')||(yes('chest.current')&&(yes('chest.dyspnea')||yes('chest.syncope')))||yes('dyspnea.current')
})
const missingFacts=computed(()=>form.symptomReports.flatMap(report=>{
  const name=displayName(report),missing:string[]=[]
  if(!report.onsetRange||report.onsetRange==='UNKNOWN')missing.push(`${name}的开始时间`)
  if(!report.course||report.course==='UNKNOWN')missing.push(`${name}的持续情况`)
  if(!report.currentStatus||report.currentStatus==='UNKNOWN')missing.push(`${name}目前是否存在`)
  if(!report.activityImpact||report.activityImpact==='UNKNOWN')missing.push(`${name}对活动的影响`)
  return missing
}))
const patientStatus:Record<string,string>={DRAFT:'草稿',SUBMITTED:'已提交',PROCESSING:'整理中',PENDING_REVIEW:'等待人工审核',REVIEWED:'已完成审核',FOLLOWUP_ACTIVE:'随访中',CLOSED:'已结束',REJECTED:'需要重新联系'}

function definition(code:string){return catalog.value.symptoms.find(item=>item.code===code)}
function displayName(report:SymptomReport){return report.customName||report.name||definition(report.symptomCode)?.name||'其他不适'}
function answer(report:SymptomReport,id:string){let value=report.answers.find(item=>item.questionId===id);if(!value){value={questionId:id,selectedOptions:[]};report.answers.push(value)}return value}
function chooseAnswer(report:SymptomReport,id:string,value:string){answer(report,id).selectedOptions=[value];if(value==='YES'){if(id==='chest.dyspnea'||id==='syncope.dyspnea')addByCode('DYSPNEA','RELATED_ANSWER');if(id==='chest.syncope')addByCode('SYNCOPE','RELATED_ANSWER');if(id==='syncope.chest_pain')addByCode('CHEST_PAIN','RELATED_ANSWER')}}
function addByCode(code:string,source:'CATALOG'|'RELATED_ANSWER'|'CUSTOM'='CATALOG'){
  if(form.symptomReports.some(item=>item.symptomCode===code))return
  const item=definition(code);if(!item)return
  const report:SymptomReport={symptomCode:code,name:item.name,source,customName:code==='OTHER'?customName.value.trim():undefined,onsetRange:'UNKNOWN',course:'UNKNOWN',currentStatus:'UNKNOWN',activityImpact:'UNKNOWN',answers:[]}
  form.symptomReports.push(report);if(!form.primarySymptomCode)form.primarySymptomCode=code;generateSummary();selectedCode.value='';customName.value=''
}
function addSelected(){if(!selectedCode.value)return;if(selectedCode.value==='OTHER'&&!customName.value.trim())return ElMessage.warning('请填写其他不适的简短名称');addByCode(selectedCode.value,selectedCode.value==='OTHER'?'CUSTOM':'CATALOG')}
function removeReport(index:number){const removed=form.symptomReports.splice(index,1)[0];if(removed?.symptomCode===form.primarySymptomCode)form.primarySymptomCode=form.symptomReports[0]?.symptomCode||'';generateSummary()}
function generateSummary(){const names=form.symptomReports.map(displayName);if(names.length)form.chiefComplaint=`主要不适为${names.join('、')}`}
function payload(){return {primarySymptomCode:form.primarySymptomCode,chiefComplaint:form.chiefComplaint.trim(),freeText:form.freeText.trim(),symptomReports:form.symptomReports.map(({id,name,supportLevel,catalogVersion,legacySeverity,legacyOnset,...report})=>report)}}
function restoreLocal(){const stored=localStorage.getItem(draftKey);if(!stored)return;try{const value=JSON.parse(stored);Object.assign(form,value);ElMessage.info('已恢复上次未提交的草稿')}catch{localStorage.removeItem(draftKey)}}
function reset(){draftId.value=null;step.value=1;Object.assign(form,{primarySymptomCode:'',chiefComplaint:'',freeText:'',symptomReports:[]});localStorage.removeItem(draftKey)}
async function load(){loading.value=true;try{const [catalogData,visitData,taskData,profileData]=await Promise.all([api.intakeCatalog(),api.myVisits(),api.myTasks(),api.patientProfile()]);catalog.value=catalogData;visits.value=visitData;tasks.value=taskData;Object.assign(profile,profileData.data);restoreLocal()}finally{loading.value=false}}
async function saveDraft(exit=false){if(!canContinue.value){ElMessage.warning('请至少选择一项不适并确认主诉摘要');return null}saving.value=true;try{const saved=draftId.value?await api.updateVisit(draftId.value,payload()):await api.createVisit(payload());draftId.value=saved.id;localStorage.setItem(draftKey,JSON.stringify(form));ElMessage.success(exit?'草稿已保存，可稍后继续':'草稿已保存');await refreshVisits();if(exit)area.value='RECORDS';return saved}catch(e:any){ElMessage.error(e.message);return null}finally{saving.value=false}}
async function submit(){const saved=await saveDraft(false);if(!saved)return;try{await ElMessageBox.confirm('提交后不能直接修改，但仍可在记录中补充信息。以上信息与我填写的一致。','确认提交',{type:urgentSignal.value?'error':'warning',confirmButtonText:'确认并提交',cancelButtonText:'返回检查'});await api.submitVisit(saved.id);ElMessage.success('已提交，正在进入人工审核');reset();area.value='RECORDS';await refreshVisits()}catch(e:any){if(e!=='cancel'&&e!=='close')ElMessage.error(e.message)}}
async function editDraft(visit:Visit){draftId.value=visit.id;Object.assign(form,{primarySymptomCode:visit.primarySymptomCode||visit.symptomReports[0]?.symptomCode||'',chiefComplaint:visit.chiefComplaint,freeText:visit.freeText,symptomReports:visit.symptomReports.map(item=>({...item,customName:item.supportLevel==='CUSTOM'?item.name:undefined,answers:item.answers.map(a=>({...a,selectedOptions:[...a.selectedOptions]}))}))});area.value='INTAKE';step.value=1;window.scrollTo({top:0,behavior:'smooth'})}
async function refreshVisits(){visits.value=await api.myVisits()}
async function addSupplement(visit:Visit){try{const result=await ElMessageBox.prompt('请只补充与本次不适有关的新信息，不要填写姓名、电话或地址。','补充信息',{confirmButtonText:'提交补充',cancelButtonText:'取消',inputType:'textarea',inputValidator:value=>!!value.trim()||'请输入补充内容'});supplementing.value=visit.id;await api.supplementVisit(visit.id,result.value);ElMessage.success('补充信息已提交，将随原记录一起审核');await refreshVisits()}catch(e:any){if(e!=='cancel'&&e!=='close')ElMessage.error(e.message)}finally{supplementing.value=null}}
async function saveProfile(){saving.value=true;try{await api.savePatientProfile({...profile});ElMessage.success('健康资料已保存，提交问诊时会保存当时的资料快照')}catch(e:any){ElMessage.error(e.message)}finally{saving.value=false}}
function listText(values:string[]){return values.join('、')}
function setList(key:'chronicConditions'|'allergies'|'longTermMedications',value:string){profile[key]=value.split(/[，,、\n]/).map(item=>item.trim()).filter(Boolean)}
watch(form,()=>{if(form.symptomReports.length)localStorage.setItem(draftKey,JSON.stringify(form))},{deep:true})
onMounted(()=>load().catch((e:any)=>ElMessage.error(e.message)))
</script>

<template>
  <section class="workspace patient-v2" v-loading="loading">
    <div class="patient-shell">
      <nav class="patient-nav" aria-label="患者功能区">
        <div class="patient-nav-title"><span>患者工作区</span><strong>我的健康事务</strong></div>
        <button v-for="item in areas" :key="item.id" type="button" :class="{active:area===item.id}" @click="area=item.id"><component :is="item.icon"/><span>{{item.label}}</span></button>
        <div class="patient-nav-note">系统整理信息而非诊断或处方；自动结果必须人工审核。</div>
      </nav>

      <main class="patient-content">
        <template v-if="area==='INTAKE'">
          <div class="page-heading patient-heading"><div><span class="eyebrow">新建预问诊</span><h1>先把情况说清楚</h1><p>按实际情况填写；不知道或说不清时可直接选择对应选项。</p></div><div class="draft-state"><CircleCheck/>草稿会自动保存在本机</div></div>
          <nav class="intake-steps" aria-label="预问诊四个阶段"><button v-for="(label,index) in ['哪里不舒服','具体情况','特别注意','检查提交']" :key="label" type="button" :class="{active:step===index+1,done:step>index+1}" @click="step=index+1"><span>{{index+1}}</span>{{label}}</button></nav>

          <article class="panel v2-intake-panel">
            <section v-if="step===1" aria-labelledby="where-title">
              <div class="section-heading"><div><span class="step-kicker">阶段 1 / 4</span><h2 id="where-title">哪里不舒服？</h2><p>可搜索或从常用症状中选择；患者无需理解内部医学代码。</p></div></div>
              <div class="catalog-tools"><el-input v-model="query" clearable placeholder="搜索症状名称，例如头痛、腹痛、咳嗽"/><el-select v-model="selectedCode" filterable placeholder="选择一项不适"><el-option v-for="item in filteredCatalog" :key="item.code" :label="`${item.name} · ${item.category}`" :value="item.code" :disabled="form.symptomReports.some(report=>report.symptomCode===item.code)"/></el-select><el-input v-if="selectedCode==='OTHER'" v-model="customName" maxlength="100" placeholder="请填写简短名称"/><el-button type="primary" :icon="Plus" @click="addSelected">添加</el-button></div>
              <div class="common-symptoms"><button v-for="item in catalog.symptoms.filter(item=>item.common)" :key="item.code" type="button" :disabled="form.symptomReports.some(report=>report.symptomCode===item.code)" @click="addByCode(item.code)"><Plus/>{{item.name}}</button></div>
              <div class="chosen-list" aria-live="polite"><article v-for="(report,index) in form.symptomReports" :key="report.symptomCode"><button class="primary-choice" type="button" :aria-pressed="form.primarySymptomCode===report.symptomCode" @click="form.primarySymptomCode=report.symptomCode"><span>{{form.primarySymptomCode===report.symptomCode?'主要不适':'设为主要'}}</span><strong>{{displayName(report)}}</strong></button><p v-if="definition(report.symptomCode)?.supportLevel!=='RULE_SUPPORTED'">该症状会被记录并交由人工复核。</p><button class="text-danger" type="button" @click="removeReport(index)">移除</button></article></div>
              <el-form-item label="主诉摘要（可以修改）" required><el-input v-model="form.chiefComplaint" maxlength="500" show-word-limit placeholder="选择不适后会自动生成，也可以用自己的话修改"/></el-form-item>
              <el-form-item label="用自己的话补充（可选）"><el-input v-model="form.freeText" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="例如什么情况下出现、还有哪些感受；不要填写姓名、电话或地址"/></el-form-item>
            </section>

            <section v-else-if="step===2" aria-labelledby="detail-title">
              <div class="section-heading"><div><span class="step-kicker">阶段 2 / 4</span><h2 id="detail-title">具体情况</h2><p>只询问发生时间、持续状态和活动影响；不同症状显示不同追问。</p></div></div>
              <article v-for="report in form.symptomReports" :key="report.symptomCode" class="fact-report"><h3>{{displayName(report)}}</h3><div class="fact-fields"><el-form-item label="什么时候开始"><el-select v-model="report.onsetRange"><el-option label="刚刚" value="JUST_NOW"/><el-option label="今天" value="TODAY"/><el-option label="1–3 天" value="ONE_TO_THREE_DAYS"/><el-option label="3 天以上" value="MORE_THAN_THREE_DAYS"/><el-option label="不知道/说不清" value="UNKNOWN"/></el-select></el-form-item><el-form-item label="持续情况"><el-select v-model="report.course"><el-option label="持续" value="CONTINUOUS"/><el-option label="间歇" value="INTERMITTENT"/><el-option label="已经缓解" value="RELIEVED"/><el-option label="不知道/说不清" value="UNKNOWN"/></el-select></el-form-item><el-form-item label="现在是否仍存在"><el-select v-model="report.currentStatus"><el-option label="仍存在" value="PRESENT"/><el-option label="目前没有" value="NOT_PRESENT"/><el-option label="不知道/说不清" value="UNKNOWN"/></el-select></el-form-item><el-form-item label="对说话、走路或正常活动的影响"><el-select v-model="report.activityImpact"><el-option label="不影响" value="NO_IMPACT"/><el-option label="需要停下休息" value="NEEDS_REST"/><el-option label="无法正常活动" value="UNABLE_NORMAL_ACTIVITY"/><el-option label="不知道/说不清" value="UNKNOWN"/></el-select></el-form-item></div>
                <div v-if="definition(report.symptomCode)?.questions.length" class="dynamic-questions"><h4>与“{{displayName(report)}}”有关的追问</h4><fieldset v-for="question in definition(report.symptomCode)?.questions" :key="question.id"><legend>{{question.prompt}}</legend><button v-for="option in question.options" :key="option.value" type="button" :class="{selected:answer(report,question.id).selectedOptions.includes(option.value)}" @click="chooseAnswer(report,question.id,option.value)">{{option.label}}</button></fieldset></div>
              </article>
            </section>

            <section v-else-if="step===3" aria-labelledby="attention-title">
              <div class="section-heading"><div><span class="step-kicker">阶段 3 / 4</span><h2 id="attention-title">需要特别注意的表现</h2><p>系统会集中核对已经填写的胸口不适、呼吸困难、真正失去意识和反应异常。</p></div></div>
              <div v-if="urgentSignal" class="urgent-notice" role="alert"><Clock/><div><strong>你填写的情况需要优先人工审核</strong><p>如症状正在发生或快速加重，请及时联系当地急救或尽快线下就医。你可以直接提交，不必继续补全非必要项目。</p></div></div>
              <div v-else-if="hasRecordOnly" class="manual-notice" role="status"><Document/><div><strong>部分不适当前仅作记录</strong><p>这不代表“常规”或“没有风险”，提交后会由人工复核。</p></div></div>
              <div v-else class="manual-notice" role="status"><CircleCheck/><div><strong>已完成重点表现核对</strong><p>没有命中已配置组合不等于安全结论，所有提交仍会进入人工审核。</p></div></div>
              <div class="attention-summary"><h3>本次已记录</h3><ul><li v-for="report in form.symptomReports" :key="report.symptomCode"><strong>{{displayName(report)}}</strong><span>{{report.currentStatus==='PRESENT'?'目前仍存在':report.currentStatus==='NOT_PRESENT'?'目前没有':'当前状态说不清'}}</span></li></ul></div>
            </section>

            <section v-else aria-labelledby="review-title">
              <div class="section-heading"><div><span class="step-kicker">阶段 4 / 4</span><h2 id="review-title">检查并提交</h2><p>这里仅使用患者语言汇总，不展示内部代码、模型或规则信息。</p></div></div>
              <div class="patient-review"><div><span>主要不适</span><strong>{{form.chiefComplaint}}</strong></div><article v-for="report in form.symptomReports" :key="report.symptomCode"><h3>{{displayName(report)}}</h3><p>开始时间：{{({'JUST_NOW':'刚刚','TODAY':'今天','ONE_TO_THREE_DAYS':'1–3 天','MORE_THAN_THREE_DAYS':'3 天以上','UNKNOWN':'说不清'} as any)[report.onsetRange||'UNKNOWN']}}</p><p>持续情况：{{({'CONTINUOUS':'持续','INTERMITTENT':'间歇','RELIEVED':'已经缓解','UNKNOWN':'说不清'} as any)[report.course||'UNKNOWN']}}</p><p>当前状态：{{({'PRESENT':'仍存在','NOT_PRESENT':'目前没有','UNKNOWN':'说不清'} as any)[report.currentStatus||'UNKNOWN']}}</p><p>活动影响：{{({'NO_IMPACT':'不影响','NEEDS_REST':'需要停下休息','UNABLE_NORMAL_ACTIVITY':'无法正常活动','UNKNOWN':'说不清'} as any)[report.activityImpact||'UNKNOWN']}}</p></article><div v-if="missingFacts.length" class="unanswered"><strong>仍有说不清的项目</strong><p>{{missingFacts.join('、')}}</p></div></div>
              <label class="agreement"><input type="checkbox" checked disabled/>以上信息与我填写的一致</label>
            </section>

            <div class="v2-form-actions"><el-button v-if="step>1" @click="step--">上一步</el-button><span/><el-button :loading="saving" :disabled="!canContinue" @click="saveDraft(true)">保存退出</el-button><el-button v-if="step<4" type="primary" :disabled="!canContinue" @click="step++">下一步</el-button><el-button v-else type="primary" size="large" :loading="saving" :disabled="!canContinue" @click="submit">提交预问诊</el-button></div>
          </article>
        </template>

        <template v-else-if="area==='RECORDS'">
          <div class="page-heading"><div><span class="eyebrow">问诊记录</span><h1>查看处理进度</h1><p>患者端只显示提交、审核与随访状态；提交后可追加补充信息。</p></div></div>
          <div v-if="!visits.length" class="panel empty-state large"><FolderOpened/><strong>还没有问诊记录</strong><p>完成一次预问诊后，处理进度会显示在这里。</p></div>
          <div class="record-grid"><article v-for="visit in visits" :key="visit.id" class="panel patient-record"><div class="record-head"><div><span>{{formatDateTime(visit.createdAt)}}</span><h2>{{visit.chiefComplaint}}</h2></div><span class="patient-status">{{patientStatus[visit.status]||visit.status}}</span></div><p>{{visit.symptomReports.map(displayName).join('、')}}</p><div v-if="visit.supplements.length" class="supplement-list"><strong>已补充 {{visit.supplements.length }} 条信息</strong><p v-for="item in visit.supplements" :key="item.id">{{formatDateTime(item.createdAt)}} · {{item.content}}</p></div><div class="record-actions"><el-button v-if="visit.status==='DRAFT'" :icon="EditPen" @click="editDraft(visit)">继续填写</el-button><el-button v-else :loading="supplementing===visit.id" @click="addSupplement(visit)">补充信息</el-button></div></article></div>
        </template>

        <template v-else-if="area==='TASKS'">
          <div class="page-heading"><div><span class="eyebrow">随访任务</span><h1>我的随访安排</h1><p>只有医务人员审核并激活计划后才会出现任务。</p></div></div>
          <div v-if="!tasks.length" class="panel empty-state large"><CircleCheck/><strong>暂无随访任务</strong><p>目前没有需要处理的随访事项。</p></div><div class="record-grid"><article v-for="task in tasks" :key="task.id" class="panel patient-record"><div class="record-head"><div><span>截止 {{formatDate(task.dueAt)}}</span><h2>{{task.title}}</h2></div><StatusPill :value="task.status"/></div><p>{{task.resultSummary||'等待随访人员处理'}}</p></article></div>
        </template>

        <template v-else>
          <div class="page-heading"><div><span class="eyebrow">健康资料</span><h1>维护必要的健康背景</h1><p>每项均可选择“无、未知、已填写”；提交问诊时会保存当时的资料快照。</p></div></div>
          <article class="panel profile-panel"><el-form label-position="top"><div class="profile-grid"><el-form-item label="年龄段"><el-select v-model="profile.ageBand"><el-option label="儿童" value="CHILD"/><el-option label="青少年" value="ADOLESCENT"/><el-option label="成年人" value="ADULT"/><el-option label="老年人" value="OLDER_ADULT"/><el-option label="未知" value="UNKNOWN"/></el-select></el-form-item><el-form-item label="必要生理信息"><el-select v-model="profile.physiologicalInfoStatus"><el-option label="无" value="NONE"/><el-option label="未知" value="UNKNOWN"/><el-option label="已填写" value="PROVIDED"/></el-select><el-input v-if="profile.physiologicalInfoStatus==='PROVIDED'" v-model="profile.physiologicalInfo" maxlength="500" placeholder="只填写与健康评估必要的信息"/></el-form-item><el-form-item label="慢性病"><el-select v-model="profile.chronicConditionsStatus"><el-option label="无" value="NONE"/><el-option label="未知" value="UNKNOWN"/><el-option label="已填写" value="PROVIDED"/></el-select><el-input v-if="profile.chronicConditionsStatus==='PROVIDED'" :model-value="listText(profile.chronicConditions)" type="textarea" placeholder="多项可用逗号分隔" @update:model-value="setList('chronicConditions',$event)"/></el-form-item><el-form-item label="过敏"><el-select v-model="profile.allergiesStatus"><el-option label="无" value="NONE"/><el-option label="未知" value="UNKNOWN"/><el-option label="已填写" value="PROVIDED"/></el-select><el-input v-if="profile.allergiesStatus==='PROVIDED'" :model-value="listText(profile.allergies)" type="textarea" placeholder="多项可用逗号分隔" @update:model-value="setList('allergies',$event)"/></el-form-item><el-form-item label="长期用药"><el-select v-model="profile.longTermMedicationsStatus"><el-option label="无" value="NONE"/><el-option label="未知" value="UNKNOWN"/><el-option label="已填写" value="PROVIDED"/></el-select><el-input v-if="profile.longTermMedicationsStatus==='PROVIDED'" :model-value="listText(profile.longTermMedications)" type="textarea" placeholder="多项可用逗号分隔" @update:model-value="setList('longTermMedications',$event)"/></el-form-item></div><el-button type="primary" size="large" :loading="saving" @click="saveProfile">保存健康资料</el-button></el-form></article>
        </template>
      </main>
    </div>
  </section>
</template>
