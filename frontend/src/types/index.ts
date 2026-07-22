export interface User {
  id: number
  username: string
  displayName: string
  phone: string
  email: string
  role: string
  enabled: boolean
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  displayName: string
  role: string
  message: string
}

export interface SimulatedPatient {
  id: number
  name: string
  age: number
  gender: string
  medicalHistory: string
  allergies: string
  bloodType: string
}

export interface Visit {
  id: number
  patient: SimulatedPatient
  doctor: any
  status: string
  chiefComplaint: string
  riskLevel: number
  triageResult: string
  doctorNotes: string
  createdAt: string
}

export interface Symptom {
  id: number
  visitId: number
  symptomName: string
  bodyPart: string
  severity: number
  duration: string
  description: string
  onsetTime: string
}

export interface TriageResult {
  id: number
  visitId: number
  riskScore: number
  riskLevel: string
  riskFactors: string
  redFlags: string
  recommendations: string
  safetyChecked: boolean
  safetyReport: string
}

export interface FollowupPlan {
  id: number
  visitId: number
  planName: string
  description: string
  intervalDays: number
  totalTimes: number
  status: string
}

export interface FollowupTask {
  id: number
  planId: number
  assigneeId: number
  dueDate: string
  questionnaire: string
  patientResponse: string
  status: string
}

export interface SafetyAlert {
  id: number
  visitId: number
  alertType: string
  severity: string
  title: string
  description: string
  evidence: string
  reviewed: boolean
  createdAt: string
}

export interface DashboardStats {
  pendingVisits: number
  activeFollowups: number
  unreviewedAlerts: number
  criticalAlerts: number
  recentVisits: VisitSummary[]
  recentAlerts: AlertSummary[]
}

export interface VisitSummary {
  id: number
  patientName: string
  status: string
  riskLevel: number
  createdAt: string
}

export interface AlertSummary {
  id: number
  title: string
  severity: string
  createdAt: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface MedicalGuideline {
  id: number
  title: string
  category: string
  content: string
  source: string
  version: string
}
