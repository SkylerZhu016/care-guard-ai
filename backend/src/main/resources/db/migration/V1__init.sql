-- =============================================================
-- V1__init.sql  基层医疗安全型预问诊与随访平台 - 全量建表
-- DB: PostgreSQL 16 + pgvector   (唯一 Schema 来源)
-- =============================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------- 认证与权限 ----------------
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    phone VARCHAR(32),
    gender VARCHAR(8),
    birth_date DATE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,       -- PATIENT/DOCTOR/FOLLOWUP/ADMIN
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,       -- e.g. visit:create / review:write
    name VARCHAR(64) NOT NULL,
    type VARCHAR(8) NOT NULL DEFAULT 'API', -- MENU/API
    parent_id BIGINT
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ---------------- 模拟患者档案 ----------------
CREATE TABLE simulated_patients (
    id BIGSERIAL PRIMARY KEY,
    patient_no VARCHAR(32) NOT NULL UNIQUE,
    owner_user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(64) NOT NULL,
    gender VARCHAR(8),
    birth_date DATE,
    phone VARCHAR(32),
    id_card VARCHAR(32),
    blood_type VARCHAR(8),
    chronic_tags JSONB NOT NULL DEFAULT '[]',
    address VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_patients_owner ON simulated_patients(owner_user_id);

CREATE TABLE patient_histories (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES simulated_patients(id) ON DELETE CASCADE,
    disease_name VARCHAR(128) NOT NULL,
    diagnosed_at DATE,
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_histories_patient ON patient_histories(patient_id);

CREATE TABLE allergy_records (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES simulated_patients(id) ON DELETE CASCADE,
    allergen VARCHAR(128) NOT NULL,
    reaction VARCHAR(255),
    severity VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_allergy_patient ON allergy_records(patient_id);

CREATE TABLE medication_records (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES simulated_patients(id) ON DELETE CASCADE,
    drug_name VARCHAR(128) NOT NULL,
    dosage VARCHAR(64),
    frequency VARCHAR(64),
    start_date DATE,
    end_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_medication_patient ON medication_records(patient_id);

-- ---------------- 预问诊 ----------------
CREATE TABLE visits (
    id BIGSERIAL PRIMARY KEY,
    visit_no VARCHAR(32) NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL REFERENCES simulated_patients(id),
    owner_user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    risk_level VARCHAR(12),
    form_data JSONB NOT NULL DEFAULT '{}',
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    submitted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_visits_patient ON visits(patient_id);
CREATE INDEX idx_visits_status ON visits(status);
CREATE INDEX idx_visits_owner ON visits(owner_user_id);

CREATE TABLE visit_status_logs (
    id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    operator_id BIGINT,
    operator_role VARCHAR(32),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vsl_visit ON visit_status_logs(visit_id);

CREATE TABLE symptoms (
    id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    body_part VARCHAR(64),
    severity VARCHAR(16),
    duration VARCHAR(64),
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_symptoms_visit ON symptoms(visit_id);

CREATE TABLE symptom_extractions (
    id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
    agent_run_id BIGINT,
    raw_text TEXT,
    extracted_json JSONB,
    missing_fields JSONB DEFAULT '[]',
    confidence NUMERIC(4,3),
    schema_version VARCHAR(16),
    model_name VARCHAR(64),
    prompt_version VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_extractions_visit ON symptom_extractions(visit_id);

CREATE TABLE triage_results (
    id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL UNIQUE REFERENCES visits(id) ON DELETE CASCADE,
    agent_run_id BIGINT,
    risk_level VARCHAR(12),
    risk_summary TEXT,
    risk_points JSONB DEFAULT '[]',
    summary_for_review JSONB,
    safety_status VARCHAR(16),
    disclaimer TEXT,
    knowledge_version VARCHAR(32),
    rule_version VARCHAR(32),
    model_name VARCHAR(64),
    prompt_version VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------- 规则引擎 ----------------
CREATE TABLE rule_definitions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(24) NOT NULL,   -- RED_FLAG/SPECIAL_GROUP/DRUG/COMBINATION/MISSING_INFO/ESCALATION
    priority INT NOT NULL DEFAULT 100,
    condition_expr JSONB NOT NULL,
    message VARCHAR(500) NOT NULL,
    risk_level VARCHAR(12) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    current_version INT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rule_versions (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES rule_definitions(id) ON DELETE CASCADE,
    version INT NOT NULL,
    condition_expr JSONB NOT NULL,
    message VARCHAR(500) NOT NULL,
    risk_level VARCHAR(12) NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (rule_id, version)
);

CREATE TABLE rule_hits (
    id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
    rule_id BIGINT,
    rule_version INT,
    rule_code VARCHAR(32),
    rule_name VARCHAR(128),
    category VARCHAR(24),
    risk_level VARCHAR(12) NOT NULL,
    message VARCHAR(500),
    evidence JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rule_hits_visit ON rule_hits(visit_id);

-- ---------------- 知识库 ----------------
CREATE TABLE knowledge_documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    org VARCHAR(128),
    publish_date DATE,
    doc_type VARCHAR(32),
    scope VARCHAR(255),
    source_note VARCHAR(500),
    file_id BIGINT,
    file_hash VARCHAR(64),
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING/ENABLED/DISABLED/INGEST_FAILED
    uploaded_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE knowledge_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    chunk_no INT NOT NULL,
    content TEXT NOT NULL,
    section VARCHAR(255),
    page_no INT,
    embedding vector(256),
    version INT NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_no, version)
);
CREATE INDEX idx_chunks_doc ON knowledge_chunks(document_id);

-- ---------------- AI 治理 ----------------
CREATE TABLE model_configs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(32) NOT NULL,          -- mock / openai-compatible
    model_name VARCHAR(64) NOT NULL,
    api_base VARCHAR(255),
    purpose VARCHAR(32) NOT NULL DEFAULT 'CHAT',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    params JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE prompt_templates (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(48) NOT NULL UNIQUE,       -- STRUCTURE/RETRIEVE/RISK/SAFETY/SUMMARY
    name VARCHAR(128) NOT NULL,
    purpose VARCHAR(64),
    current_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE prompt_versions (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES prompt_templates(id) ON DELETE CASCADE,
    version INT NOT NULL,
    content TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (template_id, version)
);

CREATE TABLE agent_runs (
    id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    current_step VARCHAR(16),
    trigger_type VARCHAR(16) NOT NULL DEFAULT 'SUBMIT',
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    total_tokens INT NOT NULL DEFAULT 0,
    model_name VARCHAR(64),
    prompt_versions JSONB DEFAULT '{}',
    knowledge_version VARCHAR(32),
    rule_version VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_agent_runs_visit ON agent_runs(visit_id);
CREATE INDEX idx_agent_runs_status ON agent_runs(status);

CREATE TABLE agent_run_steps (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES agent_runs(id) ON DELETE CASCADE,
    step VARCHAR(16) NOT NULL,              -- STRUCTURE/RETRIEVE/RISK/SAFETY/SUMMARY
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING', -- RUNNING/SUCCESS/FAILED/SKIPPED
    input_json JSONB,
    output_json JSONB,
    tokens INT NOT NULL DEFAULT 0,
    duration_ms INT,
    error TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_steps_run ON agent_run_steps(run_id);

CREATE TABLE citations (
    id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
    agent_run_id BIGINT,
    chunk_id BIGINT,
    document_id BIGINT,
    title VARCHAR(255),
    section VARCHAR(255),
    page_no INT,
    snippet TEXT,
    score DOUBLE PRECISION,
    knowledge_version VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_citations_visit ON citations(visit_id);

CREATE TABLE safety_alerts (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    level VARCHAR(12) NOT NULL DEFAULT 'MEDIUM',
    visit_id BIGINT,
    agent_run_id BIGINT,
    description VARCHAR(1000),
    detail JSONB DEFAULT '{}',
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN',  -- OPEN/ACK/CLOSED
    handled_by BIGINT,
    handled_at TIMESTAMPTZ,
    handle_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_alerts_status ON safety_alerts(status);

-- ---------------- 审核 ----------------
CREATE TABLE review_records (
    id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
    reviewer_id BIGINT NOT NULL REFERENCES users(id),
    action VARCHAR(16) NOT NULL,            -- APPROVE/REJECT/REQUEST_INFO
    comment TEXT,
    modified_summary JSONB,
    modified_risk_level VARCHAR(12),
    ai_snapshot JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reviews_visit ON review_records(visit_id);

-- ---------------- 随访 ----------------
CREATE TABLE followup_plans (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES simulated_patients(id),
    visit_id BIGINT REFERENCES visits(id),
    created_by BIGINT NOT NULL REFERENCES users(id),
    plan_name VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    interval_days INT NOT NULL DEFAULT 7,
    start_date DATE,
    end_condition VARCHAR(255),
    items JSONB NOT NULL DEFAULT '[]',
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_plans_patient ON followup_plans(patient_id);

CREATE TABLE followup_tasks (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES followup_plans(id) ON DELETE CASCADE,
    patient_id BIGINT NOT NULL REFERENCES simulated_patients(id),
    assignee_id BIGINT REFERENCES users(id),
    title VARCHAR(128) NOT NULL,
    content VARCHAR(1000),
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    risk_level VARCHAR(12),
    version INT NOT NULL DEFAULT 0,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_plan ON followup_tasks(plan_id);
CREATE INDEX idx_tasks_status_due ON followup_tasks(status, due_date);
CREATE INDEX idx_tasks_patient ON followup_tasks(patient_id);

CREATE TABLE followup_records (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES followup_tasks(id) ON DELETE CASCADE,
    patient_id BIGINT NOT NULL REFERENCES simulated_patients(id),
    contact_result VARCHAR(32),
    symptom_change VARCHAR(24),            -- IMPROVED/STABLE/WORSE/OTHER
    note TEXT,
    feedback TEXT,
    recorded_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_records_task ON followup_records(task_id);
CREATE INDEX idx_records_patient ON followup_records(patient_id);

-- ---------------- 系统 ----------------
CREATE TABLE uploaded_files (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    bucket VARCHAR(64) NOT NULL,
    content_type VARCHAR(128),
    size BIGINT,
    sha256 VARCHAR(64),
    uploaded_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    role VARCHAR(32),
    action VARCHAR(64) NOT NULL,
    object_type VARCHAR(64),
    object_id VARCHAR(64),
    before_summary VARCHAR(1000),
    after_summary VARCHAR(1000),
    ip VARCHAR(64),
    trace_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_time ON audit_logs(created_at);
CREATE INDEX idx_audit_user ON audit_logs(user_id);

CREATE TABLE system_configs (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(64) NOT NULL UNIQUE,
    config_value TEXT,
    description VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
