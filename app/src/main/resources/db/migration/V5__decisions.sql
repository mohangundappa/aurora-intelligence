CREATE TABLE IF NOT EXISTS decisions (
  id BIGSERIAL PRIMARY KEY,
  action VARCHAR(120) NOT NULL,
  experience VARCHAR(120) NOT NULL,
  channel VARCHAR(80) NOT NULL,
  reason_codes JSONB NOT NULL,
  decision_version VARCHAR(80) NOT NULL,
  experiment_id VARCHAR(120),
  explanation TEXT NOT NULL,
  session_id VARCHAR(200) NOT NULL,
  correlation_id VARCHAR(200) NOT NULL,
  inputs JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS decisions_correlation_idx ON decisions(correlation_id);
