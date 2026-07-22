import http from '@/utils/http'
import type { LoginRequest, LoginResponse, DashboardStats, Visit, TriageResult, Symptom, FollowupPlan, FollowupTask, SafetyAlert } from '@/types'

export const authApi = {
  login: (data: LoginRequest) => http.post<LoginResponse>('/auth/login', data),
  init: () => http.post<string>('/auth/init'),
}

export const dashboardApi = {
  getStats: () => http.get<DashboardStats>('/dashboard/stats'),
}

export const consultApi = {
  submit: (data: any) => http.post<Visit>('/consult/submit', data),
  getPending: () => http.get<Visit[]>('/consult/pending'),
  getTriage: (visitId: number) => http.get<TriageResult>(`/consult/${visitId}/triage`),
  getSymptoms: (visitId: number) => http.get<Symptom[]>(`/consult/${visitId}/symptoms`),
  approve: (visitId: number, notes: string) => http.put<Visit>(`/consult/${visitId}/approve`, null, { params: { notes } }),
}

export const followupApi = {
  createPlan: (data: { visitId: number; name: string; desc?: string; intervalDays?: number; totalTimes?: number }) =>
    http.post<FollowupPlan>('/followup/plan', null, { params: data }),
  getPlans: (visitId: number) => http.get<FollowupPlan[]>(`/followup/plan/${visitId}`),
  getTasks: (planId: number) => http.get<FollowupTask[]>(`/followup/tasks/${planId}`),
  completeTask: (taskId: number, response: string) =>
    http.put<FollowupTask>(`/followup/tasks/${taskId}/complete`, null, { params: { response } }),
}

export const safetyApi = {
  getAlerts: (unreviewedOnly = false) => http.get<SafetyAlert[]>('/safety/alerts', { params: { unreviewedOnly } }),
  getCount: () => http.get<number>('/safety/alerts/count'),
  review: (id: number) => http.put<SafetyAlert>(`/safety/alerts/${id}/review`),
}
