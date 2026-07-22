import dayjs from 'dayjs'

export const DISCLAIMER = '本系统为教学用辅助系统，所有 AI 生成内容仅供教学参考，不能替代医生诊断。'

export function fmtTime(t?: string | null): string {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-'
}
export function fmtDate(t?: string | null): string {
  return t ? dayjs(t).format('YYYY-MM-DD') : '-'
}

export const riskMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  LOW: { label: '低风险', type: 'success' },
  MEDIUM: { label: '中风险', type: 'warning' },
  HIGH: { label: '高风险', type: 'danger' },
  CRITICAL: { label: '危急', type: 'danger' }
}

export const visitStatusMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' | 'primary' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  SUBMITTED: { label: '已提交', type: 'primary' },
  STRUCTURING: { label: '结构化处理中', type: 'warning' },
  RULE_SCREENED: { label: '规则预筛完成', type: 'warning' },
  AI_ANALYZING: { label: 'AI 分析中', type: 'warning' },
  PENDING_REVIEW: { label: '待医务审核', type: 'primary' },
  NEED_INFO: { label: '需补充信息', type: 'warning' },
  REVIEWED: { label: '已审核', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  ARCHIVED: { label: '已归档', type: 'info' },
  FAILED: { label: '处理失败', type: 'danger' }
}

export const runStatusMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' | 'primary' }> = {
  PENDING: { label: '待执行', type: 'info' },
  RUNNING: { label: '执行中', type: 'primary' },
  PARTIAL: { label: '部分完成', type: 'warning' },
  REVIEW_FAILED: { label: '安全审查不通过', type: 'danger' },
  COMPLETED: { label: '已完成', type: 'success' },
  FAILED: { label: '已失败', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' },
  TIMEOUT: { label: '已超时', type: 'danger' }
}

export const planStatusMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' | 'primary' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_START: { label: '待启动', type: 'warning' },
  ACTIVE: { label: '执行中', type: 'primary' },
  PAUSED: { label: '已暂停', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  TERMINATED: { label: '已终止', type: 'info' }
}

export const taskStatusMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' | 'primary' }> = {
  PENDING: { label: '待处理', type: 'info' },
  ASSIGNED: { label: '已分配', type: 'primary' },
  IN_PROGRESS: { label: '处理中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  DELAYED: { label: '已延期', type: 'warning' },
  LOST: { label: '失联', type: 'danger' },
  ESCALATED: { label: '已升级', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

export const severityMap: Record<string, string> = { MILD: '轻度', MODERATE: '中度', SEVERE: '重度' }
export const specialGroupMap: Record<string, string> = {
  NONE: '无', PREGNANT: '孕产妇', ELDERLY: '老年人', INFANT: '婴幼儿', CHRONIC: '慢病患者'
}
export const symptomChangeMap: Record<string, string> = {
  IMPROVED: '好转', STABLE: '平稳', WORSE: '加重', OTHER: '其他'
}
export const ruleCategoryMap: Record<string, string> = {
  RED_FLAG: '红旗症状', SPECIAL_GROUP: '特殊人群', DRUG: '药物禁忌',
  COMBINATION: '症状组合', MISSING_INFO: '信息缺失', ESCALATION: '风险升级'
}
export const alertTypeMap: Record<string, string> = {
  REVIEW_FAILED: 'AI审查失败', PROMPT_INJECTION: '提示词攻击', PRIVACY_LEAK: '隐私泄露拦截',
  UNAUTHORIZED_ACCESS: '越权访问', MODEL_ERROR: '模型异常', MODEL_TIMEOUT: '模型超时',
  JSON_PARSE_ERROR: 'JSON解析失败', CITATION_MISSING: '引用缺失', ESCALATION: '随访升级'
}
export const docStatusMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  PENDING: { label: '摄取中', type: 'warning' },
  ENABLED: { label: '已启用', type: 'success' },
  DISABLED: { label: '已停用', type: 'info' },
  INGEST_FAILED: { label: '摄取失败', type: 'danger' }
}
export const reviewActionMap: Record<string, string> = {
  APPROVE: '通过', REJECT: '驳回', REQUEST_INFO: '要求补充'
}
export const stepNameMap: Record<string, string> = {
  STRUCTURE: '症状结构化', RETRIEVE: '指南检索', RISK: '风险分析', SAFETY: '安全审查', SUMMARY: '汇总摘要'
}

export const commonSymptoms = [
  '发热', '咳嗽', '头痛', '胸痛', '胸闷', '呼吸困难', '气促', '腹痛', '腹泻', '呕吐', '恶心',
  '乏力', '头晕', '皮疹', '咽痛', '流涕', '肌肉酸痛', '心悸', '出汗', '食欲不振'
]
