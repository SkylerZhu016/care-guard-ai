"""AI 输出 JSON Schemas（pydantic v2 强校验）—— 与 docs/05_AI_RAG_AGENT_PLAN.md 一致"""
from __future__ import annotations
from typing import Optional
from pydantic import BaseModel, Field


# ---------- 3.1 症状结构化 ----------
class StructuredSymptom(BaseModel):
    name: str
    body_part: Optional[str] = None
    severity: Optional[str] = None  # MILD/MODERATE/SEVERE
    duration: Optional[str] = None


class SymptomExtractionSchema(BaseModel):
    schema_version: str = "1.0"
    chief_complaint: str
    symptoms: list[StructuredSymptom] = []
    onset_time: Optional[str] = None
    duration_text: Optional[str] = None
    triggers: Optional[str] = None
    relief_factors: Optional[str] = None
    aggravating_factors: Optional[str] = None
    accompanying: list[str] = []
    denied: list[str] = []
    past_history: list[str] = []
    allergies: list[str] = []
    medications: list[str] = []
    special_group: str = "NONE"
    missing_fields: list[str] = []
    confidence: float = Field(ge=0.0, le=1.0)


# ---------- 3.2 检索请求 ----------
class RetrievalQuery(BaseModel):
    schema_version: str = "1.0"
    queries: list[str]
    top_k: int = 5


# ---------- 3.3 引用 ----------
class CitationSchema(BaseModel):
    schema_version: str = "1.0"
    chunk_id: int
    document_id: int
    title: str
    section: Optional[str] = None
    page_no: Optional[int] = None
    snippet: str
    score: float
    knowledge_version: Optional[str] = None


# ---------- 3.4 风险分析 ----------
class RiskPoint(BaseModel):
    point: str
    basis: str
    citation_ids: list[str] = []
    severity: str = "LOW"


class RiskAnalysisSchema(BaseModel):
    schema_version: str = "1.0"
    risk_level: str  # LOW/MEDIUM/HIGH/CRITICAL
    risk_summary: str
    risk_points: list[RiskPoint] = []
    questions_for_doctor: list[str] = []
    red_flags_noticed: list[str] = []


# ---------- 3.5 安全审查 ----------
class SafetyIssue(BaseModel):
    type: str  # DIAGNOSIS/PRESCRIPTION/NO_DISCLAIMER/REDFLAG_MISSED/NO_CITATION/INJECTION/PROMPT_LEAK/PII_LEAK/HALLUCINATION
    detail: str
    severity: str = "MEDIUM"  # HIGH/MEDIUM/LOW


class SafetyReviewSchema(BaseModel):
    schema_version: str = "1.0"
    verdict: str  # PASS / FAIL
    issues: list[SafetyIssue] = []
    sanitized: bool = False


# ---------- 3.6 医生审核摘要 ----------
class ReviewSummarySchema(BaseModel):
    schema_version: str = "1.0"
    chief_complaint: str = ""
    symptom_table: list[StructuredSymptom] = []
    risk_level: str = "LOW"
    risk_points: list[RiskPoint] = []
    citations: list[CitationSchema] = []
    suggested_focus: list[str] = []
    disclaimer: str = "本内容由 AI 生成，仅供教学参考，不能替代医生诊断。"


# ---------- 随访建议（增强，预留） ----------
class FollowupAdviceItem(BaseModel):
    topic: str
    content: str
    source_citation_id: Optional[str] = None


class FollowupAdviceSchema(BaseModel):
    schema_version: str = "1.0"
    advices: list[FollowupAdviceItem] = []
    disclaimer: str = "本内容由 AI 生成，仅供教学参考，不能替代医生诊断。"
