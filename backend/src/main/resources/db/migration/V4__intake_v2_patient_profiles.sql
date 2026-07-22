ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
UPDATE users SET role = 'PATIENT' WHERE role = 'SIMULATED_PATIENT';
ALTER TABLE users
  ADD CONSTRAINT users_role_check
  CHECK (role IN ('PATIENT','CLINICIAN','FOLLOWUP_STAFF','ADMIN'));

ALTER TABLE visits
  ADD COLUMN intake_version VARCHAR(20) NOT NULL DEFAULT 'INTAKE_V1',
  ADD COLUMN primary_symptom_code VARCHAR(60),
  ADD COLUMN profile_snapshot JSONB;

ALTER TABLE symptoms RENAME COLUMN severity TO legacy_severity;
ALTER TABLE symptoms ALTER COLUMN legacy_severity DROP NOT NULL;
ALTER TABLE symptoms
  ADD COLUMN catalog_version VARCHAR(40),
  ADD COLUMN support_level VARCHAR(24),
  ADD COLUMN report_source VARCHAR(24),
  ADD COLUMN onset_range VARCHAR(24),
  ADD COLUMN course VARCHAR(24),
  ADD COLUMN current_status VARCHAR(24),
  ADD COLUMN activity_impact VARCHAR(32),
  ADD COLUMN answers_json JSONB;

UPDATE symptoms
SET catalog_version = 'legacy-v1',
    support_level = CASE WHEN code IN ('CHEST_PAIN','DYSPNEA','SYNCOPE','ALTERED_CONSCIOUSNESS')
                         THEN 'RULE_SUPPORTED' ELSE 'RECORD_ONLY' END,
    report_source = 'LEGACY';

ALTER TABLE symptoms
  ADD CONSTRAINT symptoms_support_level_check
  CHECK (support_level IS NULL OR support_level IN ('RULE_SUPPORTED','RECORD_ONLY','CUSTOM'));

ALTER TABLE triage_results ALTER COLUMN rule_urgency DROP NOT NULL;
ALTER TABLE triage_results
  ADD COLUMN coverage_status VARCHAR(16),
  ADD COLUMN assessment_status VARCHAR(32);

UPDATE triage_results
SET coverage_status = 'FULL', assessment_status = 'RULE_EVALUATED'
WHERE coverage_status IS NULL;

ALTER TABLE triage_results
  ADD CONSTRAINT triage_coverage_status_check
  CHECK (coverage_status IS NULL OR coverage_status IN ('FULL','PARTIAL','NONE')),
  ADD CONSTRAINT triage_assessment_status_check
  CHECK (assessment_status IS NULL OR assessment_status IN ('RULE_EVALUATED','REQUIRES_MANUAL_REVIEW'));

CREATE TABLE patient_profiles (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  profile_data JSONB NOT NULL DEFAULT '{}'::jsonb,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE visit_supplements (
  id UUID PRIMARY KEY,
  visit_id UUID NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
  owner_id UUID NOT NULL REFERENCES users(id),
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_visit_supplements_visit ON visit_supplements(visit_id, created_at);

ALTER TABLE followup_plans DROP CONSTRAINT IF EXISTS chk_followup_template;
UPDATE followup_plans SET template_code = 'GENERAL_FOLLOWUP_V1'
WHERE template_code = 'HYPERTENSION_TEACHING_V1';
ALTER TABLE followup_plans
  ADD CONSTRAINT chk_followup_template CHECK (template_code IN ('GENERAL_FOLLOWUP_V1'));
