CREATE TABLE users (
  id UUID PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  display_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(32) NOT NULL CHECK (role IN ('SIMULATED_PATIENT','CLINICIAN','FOLLOWUP_STAFF','ADMIN')),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE visits (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES users(id),
  status VARCHAR(32) NOT NULL,
  chief_complaint VARCHAR(500) NOT NULL,
  free_text VARCHAR(2000) NOT NULL DEFAULT '',
  submitted_at TIMESTAMPTZ,
  idempotency_key VARCHAR(100) UNIQUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE symptoms (
  id UUID PRIMARY KEY,
  visit_id UUID NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
  code_system VARCHAR(40) NOT NULL DEFAULT 'LOCAL_SYMPTOM_V1',
  code VARCHAR(60) NOT NULL,
  name VARCHAR(100) NOT NULL,
  severity INTEGER NOT NULL CHECK (severity BETWEEN 0 AND 10),
  onset_text VARCHAR(100)
);

CREATE TABLE triage_results (
  id UUID PRIMARY KEY,
  visit_id UUID NOT NULL UNIQUE REFERENCES visits(id),
  rule_urgency VARCHAR(20) NOT NULL,
  ai_urgency VARCHAR(20),
  final_urgency VARCHAR(20),
  rule_reason_codes TEXT NOT NULL,
  ai_summary TEXT,
  review_decision VARCHAR(20),
  review_reason VARCHAR(1000),
  reviewer_id UUID REFERENCES users(id),
  reviewed_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE agent_runs (
  id UUID PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL UNIQUE,
  visit_id UUID NOT NULL REFERENCES visits(id),
  status VARCHAR(20) NOT NULL,
  provider VARCHAR(40) NOT NULL,
  model_name VARCHAR(80) NOT NULL,
  prompt_version VARCHAR(40) NOT NULL,
  knowledge_base_version VARCHAR(40) NOT NULL,
  rule_set_version VARCHAR(40) NOT NULL,
  output_hash VARCHAR(64),
  safety_decision VARCHAR(20),
  safety_reason_codes TEXT,
  duration_ms BIGINT,
  error_code VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE citations (
  id UUID PRIMARY KEY,
  agent_run_id UUID NOT NULL REFERENCES agent_runs(id) ON DELETE CASCADE,
  guideline_id VARCHAR(80) NOT NULL,
  chunk_id VARCHAR(80) NOT NULL,
  claim_key VARCHAR(80) NOT NULL,
  title VARCHAR(200) NOT NULL,
  section_name VARCHAR(200) NOT NULL,
  quote_text TEXT NOT NULL
);

CREATE TABLE followup_plans (
  id UUID PRIMARY KEY,
  visit_id UUID NOT NULL REFERENCES visits(id),
  status VARCHAR(20) NOT NULL,
  template_code VARCHAR(60) NOT NULL,
  owner_id UUID NOT NULL REFERENCES users(id),
  activated_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE followup_tasks (
  id UUID PRIMARY KEY,
  plan_id UUID NOT NULL REFERENCES followup_plans(id) ON DELETE CASCADE,
  task_code VARCHAR(60) NOT NULL,
  title VARCHAR(200) NOT NULL,
  due_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(20) NOT NULL,
  assignee_id UUID REFERENCES users(id),
  result_summary VARCHAR(1000),
  completed_at TIMESTAMPTZ
);

CREATE TABLE safety_alerts (
  id UUID PRIMARY KEY,
  run_id VARCHAR(64),
  visit_id UUID REFERENCES visits(id),
  category VARCHAR(60) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  reason_codes TEXT NOT NULL,
  redacted_summary VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
  id UUID PRIMARY KEY,
  actor_id UUID REFERENCES users(id),
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(60) NOT NULL,
  target_id UUID,
  request_id VARCHAR(64) NOT NULL,
  result VARCHAR(20) NOT NULL,
  metadata_json VARCHAR(2000) NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_visits_queue ON visits(status, submitted_at);
CREATE INDEX idx_visits_owner ON visits(owner_id, created_at DESC);
CREATE INDEX idx_followup_tasks_queue ON followup_tasks(status, due_at);
CREATE INDEX idx_audit_target ON audit_logs(target_type, target_id, created_at);
CREATE INDEX idx_audit_request ON audit_logs(request_id);

