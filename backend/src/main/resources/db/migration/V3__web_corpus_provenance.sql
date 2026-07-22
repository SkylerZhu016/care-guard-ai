ALTER TABLE guideline_versions
  ADD COLUMN fetched_at TIMESTAMPTZ,
  ADD COLUMN retrieval_method VARCHAR(80) NOT NULL DEFAULT 'legacy-seed',
  ADD COLUMN source_status VARCHAR(40) NOT NULL DEFAULT 'INGESTED',
  ADD COLUMN http_status INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN etag VARCHAR(500),
  ADD COLUMN last_modified VARCHAR(200);

CREATE INDEX idx_guideline_versions_fetched_at ON guideline_versions(fetched_at DESC);
