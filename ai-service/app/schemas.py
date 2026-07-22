from enum import Enum
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field, field_validator


class Urgency(str, Enum):
    ROUTINE = "ROUTINE"
    URGENT = "URGENT"
    EMERGENCY = "EMERGENCY"


class SafetyDecision(str, Enum):
    PASS = "PASS"
    BLOCK = "BLOCK"
    REVIEW = "REVIEW"


class SupportLevel(str, Enum):
    RULE_SUPPORTED = "RULE_SUPPORTED"
    RECORD_ONLY = "RECORD_ONLY"
    CUSTOM = "CUSTOM"


class CoverageStatus(str, Enum):
    FULL = "FULL"
    PARTIAL = "PARTIAL"
    NONE = "NONE"


class AssessmentStatus(str, Enum):
    RULE_EVALUATED = "RULE_EVALUATED"
    REQUIRES_MANUAL_REVIEW = "REQUIRES_MANUAL_REVIEW"


class QuestionAnswer(BaseModel):
    questionId: str
    selectedOptions: List[str] = []
    supplementalText: Optional[str] = None


class Symptom(BaseModel):
    codeSystem: str = "LOCAL_SYMPTOM_V2"
    code: str
    name: str = ""
    supportLevel: SupportLevel
    source: str = "CATALOG"
    onsetRange: str = "UNKNOWN"
    course: str = "UNKNOWN"
    currentStatus: str = "UNKNOWN"
    activityImpact: str = "UNKNOWN"
    answers: List[QuestionAnswer] = []


class AnalysisRequest(BaseModel):
    runId: str = Field(min_length=8, max_length=64)
    visitId: str = Field(min_length=8, max_length=64)
    ageBand: str = "ADULT"
    chiefComplaint: str = Field(min_length=1, max_length=500)
    symptoms: List[Symptom] = Field(min_length=1, max_length=30)
    freeText: str = Field(default="", max_length=2000)
    ruleUrgency: Optional[Urgency] = None
    ruleReasonCodes: List[str] = []
    coverageStatus: CoverageStatus
    assessmentStatus: AssessmentStatus

    @field_validator("freeText", "chiefComplaint")
    @classmethod
    def strip_control_chars(cls, value: str) -> str:
        return "".join(ch for ch in value.strip() if ch in "\n\t" or ord(ch) >= 32)


class Citation(BaseModel):
    guidelineId: str
    chunkId: str
    claimKey: str
    quote: str
    title: str
    section: str
    sourceUrl: str
    licenseNote: str


class SafetyResult(BaseModel):
    decision: SafetyDecision
    reasonCodes: List[str]


class AnalysisResult(BaseModel):
    caseSummary: str
    proposedUrgency: Optional[Urgency] = None
    rationale: List[str]
    missingQuestions: List[str]
    citations: List[Citation]
    safety: SafetyResult
    disclaimer: str = "系统仅整理信息，不构成诊断、处方或医疗建议；自动结果必须由人工审核"
    provider: str = "fake"
    model: str = "fake-v1"
    outputHash: str
    versions: Dict[str, str]
    agentTrace: List[str] = []


class JobAccepted(BaseModel):
    jobId: str
    runId: str
    status: str


class JobStatus(BaseModel):
    jobId: str
    runId: str
    status: str
    result: Optional[AnalysisResult] = None
    errorCode: Optional[str] = None
    durationMs: Optional[int] = None


class WorkflowState(Dict[str, Any]):
    pass

