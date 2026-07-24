const REASON_LABELS: Record<string,string> = {
  CHEST_PAIN_WITH_DYSPNEA:'胸口不适并同时呼吸困难',
  CHEST_PAIN_WITH_SYNCOPE:'胸口不适并曾真正失去意识',
  ALTERED_CONSCIOUSNESS_PRESENT:'存在反应迟钝、答非所问或定向异常',
  DYSPNEA_PRESENT:'当前存在呼吸困难',
  SUPPORTED_NO_RULE_MATCH_REQUIRES_REVIEW:'当前自动规则未覆盖该填写组合，等待人工复核',
  UNSUPPORTED_SYMPTOMS_REQUIRE_REVIEW:'部分不适仅作记录，等待人工复核',
}

const TASK_LABELS: Record<string,{label:string;description:string}> = {
  BP_RECORD:{label:'血压记录',description:'记录本次随访中的血压数据'},
  SYMPTOM_CHECK:{label:'症状复核',description:'按计划复核症状变化'},
  ADHERENCE_CHECK:{label:'计划执行复核',description:'记录随访计划执行情况'},
  CLINICIAN_REVIEW:{label:'医务人员复核',description:'由医务人员完成阶段复核'},
}

export type BirthSex = 'MALE'|'FEMALE'|'INTERSEX_OR_OTHER'|'UNKNOWN'|'PREFER_NOT_TO_SAY'
export type ReproductiveStatus = 'NOT_APPLICABLE'|'NOT_PREGNANT'|'POSSIBLY_PREGNANT'|'PREGNANT'|'POSTPARTUM_SIX_WEEKS'|'BREASTFEEDING'|'UNKNOWN'|'PREFER_NOT_TO_SAY'
export interface PhysiologicalSelection { birthSex:BirthSex; reproductiveStatus:ReproductiveStatus }

export const BIRTH_SEX_OPTIONS:Array<{value:BirthSex;label:string}> = [
  {value:'MALE',label:'男'},
  {value:'FEMALE',label:'女'},
  {value:'INTERSEX_OR_OTHER',label:'其他/未明确'},
  {value:'UNKNOWN',label:'不确定'},
  {value:'PREFER_NOT_TO_SAY',label:'暂不回答'},
]

export const REPRODUCTIVE_STATUS_OPTIONS:Array<{value:ReproductiveStatus;label:string}> = [
  {value:'NOT_APPLICABLE',label:'不适用'},
  {value:'NOT_PREGNANT',label:'目前未怀孕'},
  {value:'POSSIBLY_PREGNANT',label:'可能怀孕'},
  {value:'PREGNANT',label:'已怀孕'},
  {value:'POSTPARTUM_SIX_WEEKS',label:'产后 6 周内'},
  {value:'BREASTFEEDING',label:'正在哺乳'},
  {value:'UNKNOWN',label:'不确定'},
  {value:'PREFER_NOT_TO_SAY',label:'暂不回答'},
]

const birthSexValues=new Set(BIRTH_SEX_OPTIONS.map(item=>item.value))
const reproductiveStatusValues=new Set(REPRODUCTIVE_STATUS_OPTIONS.map(item=>item.value))
const birthSexLabels=Object.fromEntries(BIRTH_SEX_OPTIONS.map(item=>[item.value,item.label])) as Record<BirthSex,string>
const reproductiveStatusLabels=Object.fromEntries(REPRODUCTIVE_STATUS_OPTIONS.map(item=>[item.value,item.label])) as Record<ReproductiveStatus,string>

export function parsePhysiologicalInfo(value?:string):PhysiologicalSelection {
  try {
    const parsed=JSON.parse(value||'{}')
    return {
      birthSex:birthSexValues.has(parsed.birthSex)?parsed.birthSex:'UNKNOWN',
      reproductiveStatus:reproductiveStatusValues.has(parsed.reproductiveStatus)?parsed.reproductiveStatus:'UNKNOWN',
    }
  } catch {
    return {birthSex:'UNKNOWN',reproductiveStatus:'UNKNOWN'}
  }
}

export function serializePhysiologicalInfo(value:PhysiologicalSelection){
  return JSON.stringify({birthSex:value.birthSex,reproductiveStatus:value.reproductiveStatus})
}

export function physiologicalInfoLabel(value?:string){
  const parsed=parsePhysiologicalInfo(value)
  return `出生时登记性别：${birthSexLabels[parsed.birthSex]}；当前生育相关情况：${reproductiveStatusLabels[parsed.reproductiveStatus]}`
}

export function reasonLabel(code:string){ return REASON_LABELS[code] || `规则记录：${code}` }
export function taskPresentation(code:string){ return TASK_LABELS[code] || {label:code,description:'按已激活计划执行并记录'} }

export function formatDateTime(value:string){ return new Date(value).toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'}) }
export function formatDate(value:string){ return new Date(value).toLocaleDateString('zh-CN',{month:'numeric',day:'numeric'}) }
