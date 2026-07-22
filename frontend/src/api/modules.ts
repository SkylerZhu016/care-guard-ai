// 全部 REST 资源 API —— 与 docs/04_API_PLAN.md 一一对应
import http from './http'
import type {
  Page, UserInfo, Role, Patient, PatientHistory, AllergyRecord, MedicationRecord,
  Visit, VisitForm, StatusLog, TriageDetail, RuleDefinition, RuleHit, Guideline, KnowledgeChunk,
  AgentRun, ReviewRecord, ReviewAction, ReviewSummary, RiskLevel,
  FollowupPlan, FollowupTask, FollowupRecord, TrendPoint, SafetyAlert, AuditLog,
  ModelConfig, PromptTemplate
} from '@/types'

type Query = Record<string, string | number | boolean | undefined>

// ---------- auth ----------
export const authApi = {
  register: (data: { username: string; password: string; realName: string; phone?: string; gender?: string; birthDate?: string }) =>
    http.post('/auth/register', data),
  changePassword: (data: { oldPassword: string; newPassword: string }) => http.put('/auth/password', data)
}

// ---------- users & roles (ADMIN) ----------
export const userApi = {
  page: (q: Query) => http.get<Page<UserInfo & { enabled: boolean }>>('/users', { params: q }),
  create: (data: { username: string; realName: string; password: string; roles: Role[]; phone?: string }) => http.post('/users', data),
  update: (id: number, data: Partial<UserInfo> & { roles?: Role[] }) => http.put(`/users/${id}`, data),
  setStatus: (id: number, enabled: boolean) => http.put(`/users/${id}/status`, { enabled }),
  resetPassword: (id: number, password: string) => http.put(`/users/${id}/password`, { password })
}
export const roleApi = {
  list: () => http.get<{ id: number; code: Role; name: string; permissions?: { id: number; code: string; name: string }[] }[]>('/roles'),
  updatePermissions: (code: string, permissionIds: number[]) => http.put(`/roles/${code}/permissions`, { permissionIds })
}

// ---------- patients ----------
export const patientApi = {
  page: (q: Query) => http.get<Page<Patient>>('/patients', { params: q }),
  create: (data: Partial<Patient>) => http.post<Patient>('/patients', data),
  detail: (id: number) => http.get<Patient>(`/patients/${id}`),
  update: (id: number, data: Partial<Patient>) => http.put(`/patients/${id}`, data),
  addHistory: (id: number, d: PatientHistory) => http.post(`/patients/${id}/histories`, d),
  addAllergy: (id: number, d: AllergyRecord) => http.post(`/patients/${id}/allergies`, d),
  addMedication: (id: number, d: MedicationRecord) => http.post(`/patients/${id}/medications`, d),
  removeSub: (id: number, kind: 'histories' | 'allergies' | 'medications', subId: number) =>
    http.delete(`/patients/${id}/${kind}/${subId}`)
}

// ---------- visits ----------
export const visitApi = {
  saveDraft: (data: { id?: number; patientId: number; formData: VisitForm }) => http.post<{ id: number; status: string }>('/visits/draft', data),
  drafts: (q: Query) => http.get<Page<Visit>>('/visits/drafts', { params: q }),
  submit: (data: { patientId: number; formData: VisitForm; idempotencyKey: string }) => http.post<{ id: number; status: string }>('/visits', data),
  page: (q: Query) => http.get<Page<Visit>>('/visits', { params: q }),
  detail: (id: number) => http.get<Visit & { statusLogs?: StatusLog[] }>(`/visits/${id}`),
  supplement: (id: number, formData: VisitForm) => http.put(`/visits/${id}/supplement`, { formData }),
  statusLogs: (id: number) => http.get<StatusLog[]>(`/visits/${id}/status-logs`),
  retry: (id: number) => http.post(`/visits/${id}/retry`),
  agentRun: (id: number) => http.get<AgentRun>(`/visits/${id}/agent-run`)
}

// ---------- triage ----------
export const triageApi = {
  detail: (visitId: number) => http.get<TriageDetail>(`/triage/visits/${visitId}`),
  extractions: (visitId: number) => http.get(`/triage/visits/${visitId}/extractions`),
  ruleHits: (visitId: number) => http.get<RuleHit[]>(`/triage/visits/${visitId}/rule-hits`),
  citations: (visitId: number) => http.get(`/triage/visits/${visitId}/citations`)
}

// ---------- rules ----------
export const ruleApi = {
  page: (q: Query) => http.get<Page<RuleDefinition>>('/rules', { params: q }),
  create: (data: Partial<RuleDefinition>) => http.post('/rules', data),
  update: (id: number, data: Partial<RuleDefinition>) => http.put(`/rules/${id}`, data),
  toggle: (id: number, enabled: boolean) => http.put(`/rules/${id}/toggle`, { enabled }),
  versions: (id: number) => http.get(`/rules/${id}/versions`),
  test: (data: { visitId?: number; formData?: VisitForm }) => http.post<RuleHit[]>('/rules/test', data)
}

// ---------- guidelines & knowledge ----------
export const guidelineApi = {
  page: (q: Query) => http.get<Page<Guideline>>('/guidelines', { params: q }),
  upload: (form: FormData) => http.post('/guidelines', form, { headers: { 'Content-Type': 'multipart/form-data' } }),
  update: (id: number, data: Partial<Guideline>) => http.put(`/guidelines/${id}`, data),
  toggle: (id: number, enabled: boolean) => http.put(`/guidelines/${id}/toggle`, { enabled }),
  remove: (id: number) => http.delete(`/guidelines/${id}`)
}
export const knowledgeApi = {
  reindex: () => http.post('/knowledge/reindex'),
  chunks: (docId: number, q?: Query) => http.get<Page<KnowledgeChunk>>(`/knowledge/documents/${docId}/chunks`, { params: q }),
  search: (query: string, topK = 5) => http.post<KnowledgeChunk[]>('/knowledge/search', { query, topK }),
  versions: () => http.get<{ version: string; updatedAt: string }[]>('/knowledge/versions')
}

// ---------- agent runs ----------
export const agentRunApi = {
  page: (q: Query) => http.get<Page<AgentRun>>('/agent-runs', { params: q }),
  detail: (id: number) => http.get<AgentRun>(`/agent-runs/${id}`),
  sseUrl: (id: number, token: string) => `/api/v1/agent-runs/${id}/events?token=${encodeURIComponent(token)}`
}

// ---------- reviews ----------
export const reviewApi = {
  queue: (q: Query) => http.get<Page<Visit>>('/reviews/queue', { params: q }),
  submit: (data: { visitId: number; action: ReviewAction; comment?: string; modifiedSummary?: ReviewSummary; modifiedRiskLevel?: RiskLevel }) =>
    http.post('/reviews', data),
  history: (visitId: number) => http.get<ReviewRecord[]>(`/reviews/visits/${visitId}`)
}

// ---------- followup ----------
export const followupApi = {
  createPlan: (data: Partial<FollowupPlan> & { visitId?: number; patientId: number }) => http.post('/followup-plans', data),
  planAction: (id: number, action: 'start' | 'pause' | 'resume' | 'terminate') => http.post(`/followup-plans/${id}/${action}`),
  planPage: (q: Query) => http.get<Page<FollowupPlan>>('/followup-plans', { params: q }),
  planDetail: (id: number) => http.get<FollowupPlan>(`/followup-plans/${id}`),
  taskPage: (q: Query) => http.get<Page<FollowupTask>>('/followup-tasks', { params: q }),
  taskDetail: (id: number) => http.get<FollowupTask>(`/followup-tasks/${id}`),
  claim: (id: number) => http.post(`/followup-tasks/${id}/claim`),
  start: (id: number) => http.post(`/followup-tasks/${id}/start`),
  complete: (id: number, record: { contactResult: string; symptomChange: string; note?: string; feedback?: string }) =>
    http.post(`/followup-tasks/${id}/complete`, { record }),
  delay: (id: number, data: { reason: string; newDueDate: string }) => http.post(`/followup-tasks/${id}/delay`, data),
  lost: (id: number, data: { reason: string }) => http.post(`/followup-tasks/${id}/lost`, data),
  escalate: (id: number, data: { reason: string }) => http.post(`/followup-tasks/${id}/escalate`, data),
  records: (patientId: number) => http.get<FollowupRecord[]>('/followup-tasks/records', { params: { patientId } }),
  trends: (patientId: number) => http.get<TrendPoint[]>('/followup-tasks/trends', { params: { patientId } })
}

// ---------- alerts & audit & admin ----------
export const alertApi = {
  page: (q: Query) => http.get<Page<SafetyAlert>>('/safety-alerts', { params: q }),
  handle: (id: number, data: { action: 'ACK' | 'CLOSE'; note?: string }) => http.post(`/safety-alerts/${id}/handle`, data)
}
export const auditApi = {
  page: (q: Query) => http.get<Page<AuditLog>>('/audit-logs', { params: q })
}
export const adminApi = {
  configs: () => http.get<{ id: number; configKey: string; configValue: string; description?: string }[]>('/admin/configs'),
  saveConfig: (key: string, value: string) => http.put(`/admin/configs/${key}`, { configValue: value }),
  models: () => http.get<ModelConfig[]>('/admin/models'),
  saveModel: (id: number, data: Partial<ModelConfig>) => http.put(`/admin/models/${id}`, data),
  prompts: () => http.get<PromptTemplate[]>('/admin/prompts'),
  createPromptVersion: (templateId: number, data: { content: string }) => http.post(`/admin/prompts/${templateId}/versions`, data),
  togglePromptVersion: (templateId: number, versionId: number, enabled: boolean) =>
    http.put(`/admin/prompts/${templateId}/versions/${versionId}`, { enabled })
}

// ---------- files ----------
export const fileApi = {
  upload: (form: FormData) => http.post<{ fileId: number; url: string }>('/files', form),
  downloadUrl: (id: number) => `/api/v1/files/${id}/download`
}
