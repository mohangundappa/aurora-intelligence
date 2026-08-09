CREATE TABLE IF NOT EXISTS experiment_exposures (
  id BIGSERIAL PRIMARY KEY,
  experiment_id VARCHAR(120) NOT NULL,
  variant VARCHAR(40) NOT NULL,
  subject_id VARCHAR(200) NOT NULL,
  session_id VARCHAR(200) NOT NULL,
  correlation_id VARCHAR(200) NOT NULL UNIQUE,
  exposed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS experiment_exposures_lookup_idx
  ON experiment_exposures(experiment_id, variant);

CREATE TABLE IF NOT EXISTS experiment_outcomes (
  event_id UUID PRIMARY KEY,
  event_name VARCHAR(120) NOT NULL,
  correlation_id VARCHAR(200) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS experiment_outcomes_correlation_idx
  ON experiment_outcomes(correlation_id, event_name);

CREATE TABLE IF NOT EXISTS signal_lifecycle (
  signal_name VARCHAR(120) PRIMARY KEY,
  version VARCHAR(40) NOT NULL,
  status VARCHAR(40) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS signal_lifecycle_audit (
  id BIGSERIAL PRIMARY KEY,
  signal_name VARCHAR(120) NOT NULL,
  version VARCHAR(40) NOT NULL,
  action VARCHAR(40) NOT NULL,
  actor VARCHAR(120) NOT NULL,
  from_status VARCHAR(40),
  to_status VARCHAR(40) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
