CREATE EXTENSION IF NOT EXISTS vector;

-- Remove invalid synthetic audit fixtures before enforcing one plan per visit.
WITH ranked AS (
  SELECT id, ROW_NUMBER() OVER (PARTITION BY visit_id ORDER BY
    CASE status WHEN 'ACTIVE' THEN 0 WHEN 'COMPLETED' THEN 1 ELSE 2 END,
    created_at, id) AS row_number
  FROM followup_plans
)
DELETE FROM followup_plans WHERE id IN (SELECT id FROM ranked WHERE row_number > 1);

UPDATE followup_plans
SET template_code = 'HYPERTENSION_TEACHING_V1'
WHERE template_code <> 'HYPERTENSION_TEACHING_V1';

UPDATE visits
SET chief_complaint = regexp_replace(
      regexp_replace(chief_complaint, '(?<![0-9])1[3-9][0-9]{9}(?![0-9])', '[已脱敏]', 'g'),
      '(?<![0-9])[0-9]{17}[0-9Xx](?![0-9])', '[已脱敏]', 'g'),
    free_text = regexp_replace(
      regexp_replace(free_text, '(?<![0-9])1[3-9][0-9]{9}(?![0-9])', '[已脱敏]', 'g'),
      '(?<![0-9])[0-9]{17}[0-9Xx](?![0-9])', '[已脱敏]', 'g');

ALTER TABLE triage_results
  ADD CONSTRAINT chk_triage_review_decision
  CHECK (review_decision IS NULL OR review_decision IN ('ACCEPT','MODIFY','REJECT'));
ALTER TABLE followup_plans
  ADD CONSTRAINT uq_followup_plan_visit UNIQUE (visit_id),
  ADD CONSTRAINT chk_followup_template CHECK (template_code IN ('HYPERTENSION_TEACHING_V1'));
ALTER TABLE followup_tasks
  ADD CONSTRAINT chk_followup_task_status CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','OVERDUE','CANCELLED'));
ALTER TABLE agent_runs ADD COLUMN agent_trace VARCHAR(500);
ALTER TABLE citations ADD COLUMN source_url VARCHAR(1000), ADD COLUMN license_note VARCHAR(1000);

CREATE TABLE guidelines (
  id UUID PRIMARY KEY,
  guideline_id VARCHAR(80) NOT NULL UNIQUE,
  title VARCHAR(300) NOT NULL,
  publisher VARCHAR(200) NOT NULL,
  source_url VARCHAR(1000) NOT NULL,
  license_note VARCHAR(1000) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE guideline_versions (
  id UUID PRIMARY KEY,
  guideline_id UUID NOT NULL REFERENCES guidelines(id) ON DELETE CASCADE,
  version_id VARCHAR(80) NOT NULL UNIQUE,
  version_label VARCHAR(80) NOT NULL,
  language VARCHAR(20) NOT NULL,
  object_key VARCHAR(500) NOT NULL,
  sha256 VARCHAR(64) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT FALSE,
  ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE guideline_chunks (
  id UUID PRIMARY KEY,
  version_id UUID NOT NULL REFERENCES guideline_versions(id) ON DELETE CASCADE,
  chunk_id VARCHAR(80) NOT NULL UNIQUE,
  section_name VARCHAR(200) NOT NULL,
  topics VARCHAR(1000) NOT NULL,
  content TEXT NOT NULL,
  embedding vector(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_guideline_active_version ON guideline_versions(guideline_id) WHERE active;
CREATE INDEX idx_guideline_chunks_embedding ON guideline_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_guideline_chunks_version ON guideline_chunks(version_id);
