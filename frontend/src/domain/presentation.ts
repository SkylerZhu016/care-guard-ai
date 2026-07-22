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

export function reasonLabel(code:string){ return REASON_LABELS[code] || `规则记录：${code}` }
export function taskPresentation(code:string){ return TASK_LABELS[code] || {label:code,description:'按已激活计划执行并记录'} }

export function formatDateTime(value:string){ return new Date(value).toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'}) }
export function formatDate(value:string){ return new Date(value).toLocaleDateString('zh-CN',{month:'numeric',day:'numeric'}) }
