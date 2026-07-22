// 全局类型定义 —— 与 docs/04_API_PLAN.md 严格一致

export type Role = 'PATIENT' | 'DOCTOR' | 'FOLLOWUP' | 'ADMIN'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type VisitStatus =
  | 'DRAFT' | 'SUBMITTED' | 'STRUCTURING' | 'RULE_SCREENED' | 'AI_ANALYZING'
  | 'PENDING_REVIEW' | 'NEED_INFO' | 'REVIEWED' | 'REJECTED' | 'ARCHIVED' | 'FAILED'
export type RunStatus = 'PENDING' | 'RUNNING' | 'PARTIAL' | 'REVIEW_FAILED' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'TIMEOUT'
export type PlanStatus = 'DRAFT' | 'PENDING_START' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'TERMINATED'
export type TaskStatus = 'PENDING' | 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'DELAYED' | 'LOST' | 'ESCALATED' | 'CANCELLED'
export type Severity = 'MILD' | 'MODERATE' | 'SEVERE'
export type SpecialGroup = 'NONE' | 'PREGNANT' | 'ELDERLY' | 'INFANT' | 'CHRONIC'
export type SymptomChange = 'IMPROVED' | 'STABLE' | 'WORSE' | 'OTHER'
export type ReviewAction = 'APPROVE' | 'REJECT' | 'REQUEST_INFO'
export type RuleCategory = 'RED_FLAG' | 'SPECIAL_GROUP' | 'DRUG' | 'COMBINATION' | 'MISSING_INFO' | 'ESCALATION'
export type AlertStatus = 'OPEN' | 'ACK' | 'CLOSED'
export type DocStatus = 'PENDING' | 'ENABLED' | 'DISABLED' | 'INGEST_FAILED'

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ApiError {
  code: number
  message: string
  traceId?: string
}

export interface UserInfo {
  id: number
  username: string
  realName: string
  roles: Role[]
  permissions?: string[]
  mustChangePassword?: boolean
}

export interface LoginResp {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserInfo
}

export interface Patient {
  id: number
  patientNo: string
  name: string
  gender?: string
  birthDate?: string
  phone?: string
  idCard?: string
  bloodType?: string
  chronicTags: string[]
  address?: string
  histories?: PatientHistory[]
  allergies?: AllergyRecord[]
  medications?: MedicationRecord[]
  createdAt?: string
}

export interface PatientHistory { id?: number; diseaseName: string; diagnosedAt?: string; note?: string }
export interface AllergyRecord { id?: number; allergen: string; reaction?: string; severity?: string }
export interface MedicationRecord { id?: number; drugName: string; dosage?: string; frequency?: string; startDate?: string; endDate?: string }

export interface VisitForm {
  basic: { height?: number | null; weight?: number | null; specialGroup: SpecialGroup }
  chiefComplaint: string
  onsetTime?: string
  duration?: string
  severity?: Severity
  accompanying: string[]
  triggers?: string
  reliefFactors?: string
  aggravatingFactors?: string
  pastHistory?: string
  allergyHistory?: string
  medication?: string
  supplement?: string
}

export interface Visit {
  id: number
  visitNo: string
  patientId: number
  patientName?: string
  status: VisitStatus
  riskLevel?: RiskLevel
  formData: VisitForm
  submittedAt?: string
  createdAt: string
  updatedAt?: string
}

export interface StatusLog {
  id: number
  fromStatus?: VisitStatus
  toStatus: VisitStatus
  operatorRole?: string
  reason?: string
  createdAt: string
}

export interface StructuredSymptom {
  name: string
  bodyPart?: string | null
  severity?: Severity | null
  duration?: string | null
}

export interface SymptomExtraction {
  id: number
  chiefComplaint?: string
  symptoms?: StructuredSymptom[]
  missingFields?: string[]
  confidence?: number
  modelName?: string
  promptVersion?: string
  createdAt?: string
  extractedJson?: Record<string, unknown>
}

export interface RuleHit {
  id: number
  ruleCode: string
  ruleName: string
  category: RuleCategory
  riskLevel: RiskLevel
  message: string
  ruleVersion?: number
  evidence?: Record<string, unknown>
  createdAt?: string
}

export interface Citation {
  id: number
  chunkId?: number
  documentId?: number
  title: string
  section?: string
  pageNo?: number
  snippet: string
  score?: number
  knowledgeVersion?: string
}

export interface RiskPoint {
  point: string
  basis: string
  citationIds?: string[]
  severity?: RiskLevel
}

export interface ReviewSummary {
  chiefComplaint?: string
  symptomTable?: StructuredSymptom[]
  riskLevel?: RiskLevel
  riskPoints?: RiskPoint[]
  citations?: Citation[]
  suggestedFocus?: string[]
  disclaimer?: string
}

export interface SafetyIssue { type: string; detail: string; severity?: string }

export interface TriageDetail {
  visitId: number
  riskLevel?: RiskLevel
  riskSummary?: string
  riskPoints?: RiskPoint[]
  summaryForReview?: ReviewSummary
  safetyStatus?: string
  safetyIssues?: SafetyIssue[]
  disclaimer?: string
  extraction?: SymptomExtraction
  ruleHits?: RuleHit[]
  citations?: Citation[]
  modelName?: string
  promptVersion?: string
  knowledgeVersion?: string
  ruleVersion?: string
}

export interface AgentRun {
  id: number
  visitId: number
  status: RunStatus
  currentStep?: string
  triggerType?: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  totalTokens?: number
  modelName?: string
  promptVersions?: Record<string, string>
  knowledgeVersion?: string
  ruleVersion?: string
  createdAt: string
  steps?: AgentRunStep[]
}

export interface AgentRunStep {
  id: number
  step: string
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED'
  inputJson?: unknown
  outputJson?: unknown
  tokens?: number
  durationMs?: number
  error?: string
  startedAt?: string
  finishedAt?: string
}

export interface ReviewRecord {
  id: number
  visitId: number
  reviewerId: number
  reviewerName?: string
  action: ReviewAction
  comment?: string
  modifiedSummary?: ReviewSummary
  modifiedRiskLevel?: RiskLevel
  aiSnapshot?: unknown
  createdAt: string
}

export interface FollowupPlan {
  id: number
  patientId: number
  patientName?: string
  visitId?: number
  planName: string
  status: PlanStatus
  intervalDays: number
  startDate?: string
  endCondition?: string
  items: { title: string; content: string }[]
  tasks?: FollowupTask[]
  createdAt: string
}

export interface FollowupTask {
  id: number
  planId: number
  patientId: number
  patientName?: string
  assigneeId?: number
  assigneeName?: string
  title: string
  content?: string
  dueDate: string
  status: TaskStatus
  riskLevel?: RiskLevel
  completedAt?: string
  createdAt: string
}

export interface FollowupRecord {
  id: number
  taskId: number
  patientId: number
  contactResult?: string
  symptomChange?: SymptomChange
  note?: string
  feedback?: string
  createdAt: string
}

export interface SafetyAlert {
  id: number
  type: string
  level: RiskLevel
  visitId?: number
  agentRunId?: number
  description: string
  detail?: Record<string, unknown>
  status: AlertStatus
  handleNote?: string
  createdAt: string
}

export interface AuditLog {
  id: number
  userId?: number
  username?: string
  role?: string
  action: string
  objectType?: string
  objectId?: string
  beforeSummary?: string
  afterSummary?: string
  ip?: string
  traceId?: string
  createdAt: string
}

export interface Guideline {
  id: number
  title: string
  org?: string
  publishDate?: string
  docType?: string
  scope?: string
  sourceNote?: string
  version: number
  status: DocStatus
  chunkCount?: number
  createdAt: string
}

export interface KnowledgeChunk {
  id: number
  documentId: number
  chunkNo: number
  content: string
  section?: string
  pageNo?: number
  score?: number
  version: number
  enabled: boolean
}

export interface RuleDefinition {
  id: number
  code: string
  name: string
  category: RuleCategory
  priority: number
  conditionExpr: Record<string, unknown>
  message: string
  riskLevel: RiskLevel
  enabled: boolean
  currentVersion: number
  createdAt?: string
}

export interface ModelConfig {
  id: number
  name: string
  provider: string
  modelName: string
  apiBase?: string
  purpose: string
  enabled: boolean
  isDefault: boolean
  params?: Record<string, unknown>
}

export interface PromptTemplate {
  id: number
  code: string
  name: string
  purpose?: string
  currentVersion: number
  versions?: PromptVersion[]
}

export interface PromptVersion {
  id: number
  version: number
  content: string
  enabled: boolean
  createdAt?: string
}

export interface TrendPoint {
  date: string
  symptomChange: SymptomChange
  value: number
  note?: string
}
