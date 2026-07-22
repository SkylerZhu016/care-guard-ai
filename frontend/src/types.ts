export type Role = 'SIMULATED_PATIENT' | 'CLINICIAN' | 'FOLLOWUP_STAFF' | 'ADMIN'
export type Urgency = 'ROUTINE' | 'URGENT' | 'EMERGENCY'
export interface User { id: string; username: string; displayName: string; role: Role }
export interface Citation { guidelineId: string; chunkId: string; claimKey: string; title: string; section: string; quote: string }
export interface Run { runId: string; status: string; provider: string; model: string; safetyDecision?: string; safetyReasons: string[]; durationMs?: number; errorCode?: string; citations: Citation[] }
export interface Triage { id: string; ruleUrgency: Urgency; aiUrgency?: Urgency; finalUrgency?: Urgency; ruleReasons: string[]; aiSummary?: string; reviewDecision?: string; reviewReason?: string }
export interface Symptom { code: string; name: string; severity: number; onset?: string }
export interface Visit { id: string; ownerId: string; status: string; chiefComplaint: string; freeText: string; symptoms: Symptom[]; triage?: Triage; runs: Run[]; createdAt: string; submittedAt?: string }
export interface Task { id: string; planId: string; taskCode: string; title: string; dueAt: string; status: string; resultSummary?: string }
export interface Alert { id: string; runId?: string; visitId?: string; category: string; severity: string; reasonCodes: string[]; redactedSummary: string; status: string; createdAt: string }
export interface Audit { id: string; action: string; targetType: string; targetId?: string; requestId: string; result: string; metadata: string; createdAt: string }

