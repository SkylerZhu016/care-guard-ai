"""SQLAlchemy 模型 —— 仅 AI 服务需要读写的表"""
from datetime import datetime
from sqlalchemy import Column, Integer, String, Text, DateTime, Float, Boolean, ForeignKey, JSON
from sqlalchemy.dialects.postgresql import JSONB
from pgvector.sqlalchemy import Vector
from app.core.db import Base
from app.core.config import get_settings

settings = get_settings()


class AgentRun(Base):
    __tablename__ = "agent_runs"
    id = Column(Integer, primary_key=True)
    visit_id = Column(Integer, nullable=False)
    status = Column(String(20), nullable=False, default="PENDING")
    current_step = Column(String(16))
    trigger_type = Column(String(16), nullable=False, default="SUBMIT")
    error_message = Column(Text)
    started_at = Column(DateTime)
    finished_at = Column(DateTime)
    total_tokens = Column(Integer, nullable=False, default=0)
    model_name = Column(String(64))
    prompt_versions = Column(JSONB, default={})
    knowledge_version = Column(String(32))
    rule_version = Column(String(32))
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow)


class AgentRunStep(Base):
    __tablename__ = "agent_run_steps"
    id = Column(Integer, primary_key=True)
    run_id = Column(Integer, ForeignKey("agent_runs.id", ondelete="CASCADE"), nullable=False)
    step = Column(String(16), nullable=False)
    status = Column(String(16), nullable=False, default="RUNNING")
    input_json = Column(JSONB)
    output_json = Column(JSONB)
    tokens = Column(Integer, nullable=False, default=0)
    duration_ms = Column(Integer)
    error = Column(Text)
    started_at = Column(DateTime)
    finished_at = Column(DateTime)
    created_at = Column(DateTime, default=datetime.utcnow)


class SymptomExtraction(Base):
    __tablename__ = "symptom_extractions"
    id = Column(Integer, primary_key=True)
    visit_id = Column(Integer, nullable=False)
    agent_run_id = Column(Integer)
    raw_text = Column(Text)
    extracted_json = Column(JSONB)
    missing_fields = Column(JSONB, default=[])
    confidence = Column(Float)
    schema_version = Column(String(16))
    model_name = Column(String(64))
    prompt_version = Column(String(16))
    created_at = Column(DateTime, default=datetime.utcnow)


class TriageResult(Base):
    __tablename__ = "triage_results"
    id = Column(Integer, primary_key=True)
    visit_id = Column(Integer, nullable=False, unique=True)
    agent_run_id = Column(Integer)
    risk_level = Column(String(12))
    risk_summary = Column(Text)
    risk_points = Column(JSONB, default=[])
    summary_for_review = Column(JSONB)
    safety_status = Column(String(16))
    disclaimer = Column(Text)
    knowledge_version = Column(String(32))
    rule_version = Column(String(32))
    model_name = Column(String(64))
    prompt_version = Column(String(16))
    created_at = Column(DateTime, default=datetime.utcnow)


class Citation(Base):
    __tablename__ = "citations"
    id = Column(Integer, primary_key=True)
    visit_id = Column(Integer, nullable=False)
    agent_run_id = Column(Integer)
    chunk_id = Column(Integer)
    document_id = Column(Integer)
    title = Column(String(255))
    section = Column(String(255))
    page_no = Column(Integer)
    snippet = Column(Text)
    score = Column(Float)
    knowledge_version = Column(String(32))
    created_at = Column(DateTime, default=datetime.utcnow)


class SafetyAlert(Base):
    __tablename__ = "safety_alerts"
    id = Column(Integer, primary_key=True)
    type = Column(String(32), nullable=False)
    level = Column(String(12), nullable=False, default="MEDIUM")
    visit_id = Column(Integer)
    agent_run_id = Column(Integer)
    description = Column(String(1000))
    detail = Column(JSONB, default={})
    status = Column(String(12), nullable=False, default="OPEN")
    handled_by = Column(Integer)
    handled_at = Column(DateTime)
    handle_note = Column(String(500))
    created_at = Column(DateTime, default=datetime.utcnow)


class KnowledgeDocument(Base):
    __tablename__ = "knowledge_documents"
    id = Column(Integer, primary_key=True)
    title = Column(String(255), nullable=False)
    org = Column(String(128))
    publish_date = Column(String)  # DATE but keep simple
    doc_type = Column(String(32))
    scope = Column(String(255))
    source_note = Column(String(500))
    file_id = Column(Integer)
    file_hash = Column(String(64))
    version = Column(Integer, nullable=False, default=1)
    status = Column(String(20), nullable=False, default="PENDING")
    uploaded_by = Column(Integer)
    deleted = Column(Boolean, nullable=False, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow)


class KnowledgeChunk(Base):
    __tablename__ = "knowledge_chunks"
    id = Column(Integer, primary_key=True)
    document_id = Column(Integer, ForeignKey("knowledge_documents.id", ondelete="CASCADE"), nullable=False)
    chunk_no = Column(Integer, nullable=False)
    content = Column(Text, nullable=False)
    section = Column(String(255))
    page_no = Column(Integer)
    embedding = Column(Vector(settings.VECTOR_DIM))
    version = Column(Integer, nullable=False, default=1)
    enabled = Column(Boolean, nullable=False, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)


class ModelConfig(Base):
    __tablename__ = "model_configs"
    id = Column(Integer, primary_key=True)
    name = Column(String(64), nullable=False, unique=True)
    provider = Column(String(32), nullable=False)
    model_name = Column(String(64), nullable=False)
    api_base = Column(String(255))
    purpose = Column(String(32), nullable=False, default="CHAT")
    enabled = Column(Boolean, nullable=False, default=True)
    is_default = Column(Boolean, nullable=False, default=False)
    params = Column(JSONB, default={})


class PromptTemplate(Base):
    __tablename__ = "prompt_templates"
    id = Column(Integer, primary_key=True)
    code = Column(String(48), nullable=False, unique=True)
    name = Column(String(128), nullable=False)
    purpose = Column(String(64))
    current_version = Column(Integer, nullable=False, default=1)


class PromptVersion(Base):
    __tablename__ = "prompt_versions"
    id = Column(Integer, primary_key=True)
    template_id = Column(Integer, ForeignKey("prompt_templates.id", ondelete="CASCADE"), nullable=False)
    version = Column(Integer, nullable=False)
    content = Column(Text, nullable=False)
    enabled = Column(Boolean, nullable=False, default=True)


class SystemConfig(Base):
    __tablename__ = "system_configs"
    id = Column(Integer, primary_key=True)
    config_key = Column(String(64), nullable=False, unique=True)
    config_value = Column(Text)
    description = Column(String(255))


class Visit(Base):
    """只读：获取问诊上下文"""
    __tablename__ = "visits"
    id = Column(Integer, primary_key=True)
    visit_no = Column(String(32))
    patient_id = Column(Integer)
    status = Column(String(24), default="DRAFT")
    risk_level = Column(String(12))
    form_data = Column(JSONB)
    version = Column(Integer, default=0)


class RuleDefinition(Base):
    """只读：规则引擎由 Spring Boot 执行，AI 服务仅读取版本信息"""
    __tablename__ = "rule_definitions"
    id = Column(Integer, primary_key=True)
    code = Column(String(32))
    current_version = Column(Integer)
    enabled = Column(Boolean, default=True)


class RuleHit(Base):
    """只读：由 Spring Boot 写入，AI 服务读取做风险合并"""
    __tablename__ = "rule_hits"
    id = Column(Integer, primary_key=True)
    visit_id = Column(Integer)
    rule_code = Column(String(32))
    rule_name = Column(String(128))
    category = Column(String(24))
    risk_level = Column(String(12))
    message = Column(String(500))
