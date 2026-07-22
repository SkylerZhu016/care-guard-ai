from pydantic import BaseModel
from typing import Optional, List

class SymptomEntry(BaseModel):
    symptom_name: str
    body_part: Optional[str] = None
    severity: int = 5
    duration: Optional[str] = None
    description: Optional[str] = None

class PreConsultInput(BaseModel):
    patient_id: int
    patient_name: str
    patient_age: int
    patient_gender: str
    patient_history: Optional[str] = None
    patient_allergies: Optional[str] = None
    chief_complaint: str
    symptoms: List[SymptomEntry] = []

class TriageOutput(BaseModel):
    risk_score: int
    risk_level: str  # LOW, MEDIUM, HIGH, CRITICAL
    risk_factors: List[str]
    red_flags: List[str]
    recommendations: str
    safety_checked: bool = False
    safety_report: str = ""

class GuidelineChunk(BaseModel):
    id: int
    title: str
    content: str
    relevance_score: float

class SafetyCheckInput(BaseModel):
    text: str
    context: Optional[str] = None

class SafetyCheckOutput(BaseModel):
    is_safe: bool
    alert_type: Optional[str] = None
    severity: Optional[str] = None
    reason: str
