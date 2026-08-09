CREATE TABLE IF NOT EXISTS derived_signals (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  value NUMERIC(10, 2) NOT NULL,
  confidence NUMERIC(5, 4) NOT NULL,
  computed_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  explanation TEXT NOT NULL,
  provenance TEXT NOT NULL,
  session_id VARCHAR(200) NOT NULL,
  customer_id VARCHAR(200),
  correlation_id VARCHAR(200) NOT NULL
);

CREATE INDEX IF NOT EXISTS derived_signals_session_idx
  ON derived_signals(session_id, computed_at DESC);
