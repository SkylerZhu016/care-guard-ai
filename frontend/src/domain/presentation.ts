import type { Visit } from '../types'

export interface SymptomOption {
  code: string
  name: string
  aliases: string[]
  category: string
  coverage: 'ACTIVE'
  coverageLabel: string
  hint: string
}

export const SYMPTOM_CATALOG: SymptomOption[] = [
  { code:'CHEST_PAIN', name:'胸痛', aliases:['胸口痛','胸部疼痛'], category:'胸部症状', coverage:'ACTIVE', coverageLabel:'组合红旗规则已配置', hint:'系统会结合呼吸困难、晕厥和严重程度执行规则筛查。' },
  { code:'DYSPNEA', name:'呼吸困难', aliases:['气短','喘不过气'], category:'呼吸症状', coverage:'ACTIVE', coverageLabel:'红旗规则已配置', hint:'系统会执行呼吸症状与严重程度规则筛查。' },
  { code:'SYNCOPE', name:'晕厥', aliases:['昏厥','晕倒'], category:'意识相关', coverage:'ACTIVE', coverageLabel:'组合红旗规则已配置', hint:'与胸痛组合时会进入急症红旗规则。' },
  { code:'ALTERED_CONSCIOUSNESS', name:'意识异常', aliases:['意识不清','反应异常'], category:'意识相关', coverage:'ACTIVE', coverageLabel:'急症红旗规则已配置', hint:'当前教学规则将新发意识异常作为急症红旗。' },
]

export const SYMPTOM_BY_CODE = new Map(SYMPTOM_CATALOG.map(item => [item.code,item]))

const REASON_LABELS: Record<string,string> = {
  CHEST_PAIN_WITH_RED_FLAG:'胸痛伴随危险信号，命中组合红旗规则',
  ALTERED_CONSCIOUSNESS_RED_FLAG:'意识异常，命中急症红旗规则',
  SEVERE_OR_RESPIRATORY_SYMPTOM:'呼吸症状或严重程度达到紧急复核阈值',
  NO_CONFIGURED_RED_FLAG:'未命中当前已配置红旗；仍需人工审核',
}

const TASK_LABELS: Record<string,{label:string;description:string}> = {
  BP_RECORD:{label:'教学血压记录',description:'记录本次高血压教学随访中的合成血压数据'},
  SYMPTOM_CHECK:{label:'结构化症状复核',description:'按模板复核合成症状变化'},
  ADHERENCE_CHECK:{label:'教学计划执行复核',description:'记录教学计划的模拟执行情况'},
  CLINICIAN_REVIEW:{label:'医务人员复核',description:'由模拟医务人员完成阶段复核'},
}

export function reasonLabel(code:string){ return REASON_LABELS[code] || `规则记录：${code}` }
export function taskPresentation(code:string){ return TASK_LABELS[code] || {label:code,description:'按已激活教学模板执行并记录'} }
export function symptomName(code:string,fallback=''){ return SYMPTOM_BY_CODE.get(code)?.name || fallback || code }
export function isSupportedSymptom(code:string){ return SYMPTOM_BY_CODE.has(code) }

export function isHypertensionTeachingCase(visit:Visit|null|undefined){
  if(!visit)return false
  const text=[visit.chiefComplaint,visit.freeText,...visit.symptoms.flatMap(s=>[s.code,s.name])].join(' ').toUpperCase()
  return text.includes('HYPERTENSION')||text.includes('高血压')||text.includes('血压升高')
}

export function formatDateTime(value:string){ return new Date(value).toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'}) }
export function formatDate(value:string){ return new Date(value).toLocaleDateString('zh-CN',{month:'numeric',day:'numeric'}) }
