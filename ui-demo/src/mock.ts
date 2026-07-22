import type { RoleMeta } from './types'

export const roles: RoleMeta[] = [
  { id: 'patient', label: '患者端', shortLabel: '患者', description: '完成预问诊、查看记录与维护健康资料' },
  { id: 'clinician', label: '医务审核', shortLabel: '医务', description: '核对事实、规则证据并完成人工终审' },
  { id: 'followup', label: '随访执行', shortLabel: '随访', description: '执行已激活任务并记录随访结果' },
  { id: 'admin', label: '安全治理', shortLabel: '治理', description: '查看告警、知识来源、运行与审计' },
]

export const symptomOptions = ['头痛', '咳嗽', '腹痛', '胸口不适', '呼吸困难', '头晕', '恶心', '其他不适']

export const reviewCases = [
  { id: 'M-0722-018', name: '测试患者 A', complaint: '胸口不适、呼吸困难', time: '12 分钟前', level: '优先', tone: 'red' as const, status: '待审核' },
  { id: 'M-0722-017', name: '测试患者 B', complaint: '头痛、恶心', time: '31 分钟前', level: '常规', tone: 'green' as const, status: '待审核' },
  { id: 'M-0722-016', name: '测试患者 C', complaint: '持续咳嗽', time: '48 分钟前', level: '需补充', tone: 'amber' as const, status: '信息不足' },
  { id: 'M-0722-015', name: '测试患者 D', complaint: '腹部不适', time: '1 小时前', level: '人工判断', tone: 'blue' as const, status: '待审核' },
]

export const followupTasks = [
  { id: 'FU-2048', title: '症状变化复核', patient: '测试患者 A', due: '今天 16:30', status: '待执行', tone: 'amber' as const, description: '确认胸口不适与呼吸困难是否仍然存在，并记录变化。' },
  { id: 'FU-2047', title: '血压记录', patient: '测试患者 E', due: '今天 18:00', status: '进行中', tone: 'blue' as const, description: '按已激活计划记录本次随访中的血压数据。' },
  { id: 'FU-2046', title: '计划执行复核', patient: '测试患者 F', due: '明天 09:30', status: '待执行', tone: 'green' as const, description: '确认近期健康管理计划执行情况并记录反馈。' },
  { id: 'FU-2045', title: '症状变化复核', patient: '测试患者 B', due: '昨天 17:00', status: '已逾期', tone: 'red' as const, description: '复核头痛与恶心的变化，必要时转交医务人员。' },
]

export const knowledgeSources = [
  { publisher: '国家卫生健康委', title: '基层医疗卫生服务能力提升工作指引', version: '2025-12', chunks: 34, status: '已验证' },
  { publisher: '世界卫生组织', title: 'Primary care patient safety technical series', version: '2025-08', chunks: 28, status: '已验证' },
  { publisher: '中华医学会', title: '常见症状基层评估共识', version: '2026-02', chunks: 46, status: '待复核' },
  { publisher: '系统内置基线', title: '红旗症状确定性规则说明', version: 'v1.4', chunks: 18, status: '已验证' },
]
