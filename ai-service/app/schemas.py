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


class Symptom(BaseModel):
    codeSystem: str = "LOCAL_SYMPTOM_V1"
    code: str
    name: str = ""
    severity: int = Field(ge=0, le=10)
    onset: Optional[str] = None


class AnalysisRequest(BaseModel):
    runId: str = Field(min_length=8, max_length=64)
    visitId: str = Field(min_length=8, max_length=64)
    ageBand: str = "ADULT"
    chiefComplaint: str = Field(min_length=1, max_length=500)
    symptoms: List[Symptom] = Field(min_length=1, max_length=30)
    freeText: str = Field(default="", max_length=2000)
    ruleUrgency: Urgency
    ruleReasonCodes: List[str] = []

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
    proposedUrgency: Urgency
    rationale: List[str]
    missingQuestions: List[str]
    citations: List[Citation]
    safety: SafetyResult
    disclaimer: str = "仅用于教学模拟，不构成诊断或医疗建议"
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

