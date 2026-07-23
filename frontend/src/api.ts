import type { Alert, Audit, ComplaintAnalysis, ComplaintFacts, ComplaintTag, Guideline, IntakeCatalog, PatientProfile, PatientProfileInput, ReviewDecision, Run, Task, TaskStatus, Urgency, User, Visit, VisitSupplement } from './types'

export class ApiError extends Error { constructor(public status: number, public code: string, message: string) { super(message) } }

export class ApiClient {
  constructor(private token: () => string | null) {}
  private async request<T>(path: string, init: RequestInit = {}, version='v1'): Promise<T> {
    const headers = new Headers(init.headers)
    if (init.body) headers.set('Content-Type', 'application/json')
    const token = this.token(); if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await fetch(`/api/${version}${path}`, { ...init, headers })
    if (!response.ok) { const error = await response.json().catch(() => ({})); throw new ApiError(response.status, error.code || 'NETWORK_ERROR', error.message || '请求失败') }
    const text = await response.text()
    return (text ? JSON.parse(text) : undefined) as T
  }
  login(username: string, password: string) { return this.request<{accessToken:string;user:User}>('/auth/login', { method:'POST', body:JSON.stringify({username,password}) }) }
  me() { return this.request<User>('/me') }
  intakeCatalog() { return this.request<IntakeCatalog>('/intake-catalog',{},'v2') }
  patientProfile() { return this.request<PatientProfile>('/patient-profile',{},'v2') }
  savePatientProfile(body:PatientProfileInput) { return this.request<PatientProfile>('/patient-profile',{method:'PUT',body:JSON.stringify(body)},'v2') }
  myVisits() { return this.request<Visit[]>('/visits/mine',{},'v2') }
  createVisit(body: unknown) { return this.request<Visit>('/visits', { method:'POST', body:JSON.stringify(body) },'v2') }
  updateVisit(id:string, body:unknown) { return this.request<Visit>(`/visits/${id}`, { method:'PUT', body:JSON.stringify(body) },'v2') }
  submitVisit(id: string) { return this.request<Visit>(`/visits/${id}/submit`, { method:'POST', headers:{'Idempotency-Key':crypto.randomUUID()} },'v2') }
  analyzeComplaint(id:string) { return this.request<ComplaintAnalysis>(`/visits/${id}/analyze-complaint`,{method:'POST'},'v2') }
  confirmComplaint(id:string,body:{normalizedSummary:string;tags:ComplaintTag[];structuredFacts:ComplaintFacts;riskSignals:string[];missingQuestions:string[];uncertainties:string[]}) { return this.request<ComplaintAnalysis>(`/visits/${id}/complaint-structure`,{method:'PUT',body:JSON.stringify(body)},'v2') }
  supplementVisit(id:string,content:string) { return this.request<VisitSupplement>(`/visits/${id}/supplements`,{method:'POST',body:JSON.stringify({content})},'v2') }
  queue() { return this.request<Visit[]>('/clinician/visits',{},'v2') }
  review(id:string, decision:ReviewDecision, reason:string, finalUrgency?:Urgency) { return this.request<Visit>(`/triage-results/${id}/review`, {method:'POST',body:JSON.stringify({decision,reason,...(decision==='REJECT'?{}:{finalUrgency})})}) }
  createPlan(visitId:string, templateCode:string) { return this.request<{id:string}>(`/followup-plans`,{method:'POST',body:JSON.stringify({visitId,templateCode})}) }
  activatePlan(id:string) { return this.request(`/followup-plans/${id}/activate`,{method:'POST'}) }
  myTasks() { return this.request<Task[]>('/followup-tasks/mine') }
  updateTask(id:string,status:TaskStatus,resultSummary='') { return this.request<Task>(`/followup-tasks/${id}`,{method:'PATCH',body:JSON.stringify({status,resultSummary})}) }
  alerts() { return this.request<Alert[]>('/admin/safety-alerts') }
  audits() { return this.request<Audit[]>('/admin/audit-logs') }
  runs() { return this.request<Run[]>('/admin/agent-runs') }
  guidelines() { return this.request<Guideline[]>('/admin/guidelines') }
  reindexGuidelines() { return this.request<void>('/admin/guidelines/reindex',{method:'POST'}) }
}

