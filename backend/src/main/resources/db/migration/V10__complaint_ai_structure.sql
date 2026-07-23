CREATE TABLE visit_complaint_analyses (
  id UUID PRIMARY KEY,
  visit_id UUID NOT NULL UNIQUE REFERENCES visits(id) ON DELETE CASCADE,
  raw_complaint TEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  structured_json JSONB,
  confirmed_json JSONB,
  provider VARCHAR(80),
  model_name VARCHAR(120),
  prompt_version VARCHAR(80) NOT NULL DEFAULT 'complaint-structure-v1',
  error_code VARCHAR(120),
  duration_ms BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT complaint_analysis_status_check
    CHECK (status IN ('PENDING','SUCCEEDED','FAILED','INVALID'))
);

CREATE INDEX idx_complaint_analysis_visit ON visit_complaint_analyses(visit_id);

ALTER TABLE triage_results ADD COLUMN ai_detail JSONB;
