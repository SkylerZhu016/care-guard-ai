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
    source: str = "USER_SELECTED"
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
    complaintStructure: Optional[Dict[str, Any]] = None
    profileSnapshot: Optional[Dict[str, Any]] = None
    history: List[Dict[str, Any]] = Field(default_factory=list, max_length=10)
    supplements: List[str] = Field(default_factory=list, max_length=20)

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
    structuredSummary: str
    keyFindings: List[str]
    abnormalSignals: List[str]
    areasToRuleOut: List[str]
    recommendedAdditionalInformation: List[str]
    riskSignals: List[str]
    evidenceSynthesis: str
    uncertainties: List[str]
    clinicalThinkingPrompts: List[str]
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


class ComplaintTag(BaseModel):
    code: str = Field(min_length=2, max_length=60, pattern=r"^[A-Z][A-Z0-9_]+$")
    displayName: str = Field(min_length=1, max_length=100)
    category: str = Field(min_length=1, max_length=60)
    source: str = Field(pattern=r"^(user_selected|ai_extracted)$")
    confidence: Optional[float] = Field(default=None, ge=0, le=1)
    evidenceText: str = Field(default="", max_length=300)
    confirmationStatus: str = Field(default="proposed", pattern=r"^(proposed|confirmed|removed)$")


class ComplaintFacts(BaseModel):
    duration: str = Field(default="", max_length=100)
    onset: str = Field(default="", max_length=100)
    location: str = Field(default="", max_length=120)
    character: str = Field(default="", max_length=120)
    aggravatingFactors: List[str] = Field(default_factory=list, max_length=12)
    relievingFactors: List[str] = Field(default_factory=list, max_length=12)
    associatedSymptoms: List[str] = Field(default_factory=list, max_length=20)
    activityImpact: str = Field(default="", max_length=120)

    @field_validator("duration", "onset", "location", "character", "activityImpact", mode="before")
    @classmethod
    def empty_unknown_text(cls, value: Any) -> str:
        return "" if value is None else str(value)

    @field_validator("aggravatingFactors", "relievingFactors", "associatedSymptoms", mode="before")
    @classmethod
    def normalize_fact_lists(cls, value: Any) -> List[str]:
        if value is None:
            return []
        if isinstance(value, str):
            return [value] if value.strip() else []
        return value


class ComplaintStructureRequest(BaseModel):
    rawComplaint: str = Field(min_length=1, max_length=2500)
    selectedTags: List[ComplaintTag] = Field(default_factory=list, max_length=30)
    ageBand: str = Field(default="UNKNOWN", max_length=40)

    @field_validator("rawComplaint")
    @classmethod
    def clean_raw_complaint(cls, value: str) -> str:
        return "".join(ch for ch in value.strip() if ch in "\n\t" or ord(ch) >= 32)


class ComplaintStructureResult(BaseModel):
    normalizedSummary: str = Field(min_length=1, max_length=500)
    extractedTags: List[ComplaintTag] = Field(default_factory=list, max_length=30)
    structuredFacts: ComplaintFacts
    riskSignals: List[str] = Field(default_factory=list, max_length=20)
    missingQuestions: List[str] = Field(default_factory=list, max_length=20)
    uncertainties: List[str] = Field(default_factory=list, max_length=20)
    provider: str
    model: str
    durationMs: int = Field(ge=0)
    disclaimer: str = "AI 仅用于整理患者表述，不能替代医生诊断；患者确认后仍需医务人员复核。"

