export type Role = 'PATIENT' | 'CLINICIAN' | 'FOLLOWUP_STAFF' | 'ADMIN'
export type Urgency = 'ROUTINE' | 'URGENT' | 'EMERGENCY'
export type SupportLevel = 'RULE_SUPPORTED' | 'RECORD_ONLY' | 'CUSTOM'
export type CoverageStatus = 'FULL' | 'PARTIAL' | 'NONE'
export type AssessmentStatus = 'RULE_EVALUATED' | 'REQUIRES_MANUAL_REVIEW'
export type ReviewDecision = 'ACCEPT' | 'MODIFY' | 'REJECT'
export type TaskStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'OVERDUE' | 'CANCELLED'
export interface User { id: string; username: string; displayName: string; role: Role }
export interface Citation { guidelineId: string; chunkId: string; claimKey: string; title: string; section: string; quote: string; sourceUrl: string; licenseNote: string }
export interface Run { runId: string; status: string; provider: string; model: string; safetyDecision?: string; safetyReasons: string[]; agentTrace: string[]; durationMs?: number; errorCode?: string; citations: Citation[] }
export interface Triage { id: string; ruleUrgency?: Urgency; aiUrgency?: Urgency; finalUrgency?: Urgency; coverageStatus?: CoverageStatus; assessmentStatus?: AssessmentStatus; ruleReasons: string[]; aiSummary?: string; reviewDecision?: ReviewDecision; reviewReason?: string }
export interface AiClinicalSupport { structuredSummary:string; keyFindings:string[]; abnormalSignals:string[]; missingQuestions:string[]; areasToRuleOut:string[]; recommendedAdditionalInformation:string[]; riskSignals:string[]; evidenceSynthesis:string; uncertainties:string[]; clinicalThinkingPrompts:string[]; disclaimer:string }
export interface ComplaintTag { code:string; displayName:string; category:string; source:'user_selected'|'ai_extracted'; confidence?:number; evidenceText?:string; confirmationStatus:'proposed'|'confirmed'|'removed' }
export interface ComplaintFacts { duration:string; onset:string; location:string; character:string; aggravatingFactors:string[]; relievingFactors:string[]; associatedSymptoms:string[]; activityImpact:string }
export interface ComplaintAnalysis { status:'PENDING'|'SUCCEEDED'|'FAILED'|'INVALID'; rawComplaint:string; normalizedSummary:string; tags:ComplaintTag[]; structuredFacts?:ComplaintFacts; riskSignals:string[]; missingQuestions:string[]; uncertainties:string[]; provider?:string; model?:string; durationMs?:number; errorCode?:string; disclaimer:string }
export interface QuestionAnswer { questionId: string; selectedOptions: string[]; supplementalText?: string }
export interface SymptomReport { id?:string; symptomCode: string; name?: string; customName?:string; source: 'USER_SELECTED'|'CATALOG'|'RELATED_ANSWER'|'CUSTOM'|'LEGACY'; catalogVersion?:string; supportLevel?:SupportLevel; onsetRange?:string; course?:string; currentStatus?:string; activityImpact?:string; answers:QuestionAnswer[]; legacySeverity?:number; legacyOnset?:string }
export interface Visit { id: string; ownerId: string; status: string; intakeVersion:string; primarySymptomCode?:string; chiefComplaint: string; freeText: string; symptomReports: SymptomReport[]; profileSnapshot?:PatientProfileInput; complaintAnalysis?:ComplaintAnalysis; triage?: Triage & {aiSupport?:AiClinicalSupport}; runs: Run[]; supplements:VisitSupplement[]; createdAt: string; submittedAt?: string }
export interface QuestionOption { value:string; label:string }
export interface CatalogQuestion { id:string; prompt:string; type:string; multiple:boolean; options:QuestionOption[] }
export interface CatalogSymptom { code:string; name:string; category:string; supportLevel:SupportLevel; common:boolean; questions:CatalogQuestion[] }
export interface IntakeCatalog { version:string; symptoms:CatalogSymptom[] }
export interface PatientProfileInput { ageBand:string; physiologicalInfoStatus:string; physiologicalInfo:string; chronicConditionsStatus:string; chronicConditions:string[]; allergiesStatus:string; allergies:string[]; longTermMedicationsStatus:string; longTermMedications:string[] }
export interface PatientProfile { id?:string; ownerId:string; version:number; data:PatientProfileInput; updatedAt?:string }
export interface VisitSupplement { id:string; content:string; createdAt:string }
export interface Task { id: string; planId: string; taskCode: string; title: string; dueAt: string; status: TaskStatus; resultSummary?: string }
export interface Alert { id: string; runId?: string; visitId?: string; category: string; severity: string; reasonCodes: string[]; redactedSummary: string; status: string; createdAt: string }
export interface Audit { id: string; action: string; targetType: string; targetId?: string; requestId: string; result: string; metadata: string; createdAt: string }
export interface Guideline { guidelineId: string; title: string; publisher: string; sourceUrl: string; licenseNote: string; activeVersion: string; chunkCount: number; objectKey: string; fetchedAt?: string; contentSha256: string; sourceStatus: string; retrievalMethod: string }

