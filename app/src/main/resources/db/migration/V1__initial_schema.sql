CREATE TABLE IF NOT EXISTS raw_events (
 event_id UUID PRIMARY KEY, event_name VARCHAR(120) NOT NULL, event_time TIMESTAMPTZ NOT NULL,
 received_time TIMESTAMPTZ NOT NULL, schema_version VARCHAR(30) NOT NULL, source VARCHAR(100) NOT NULL,
 session_id VARCHAR(200) NOT NULL, anonymous_id VARCHAR(200) NOT NULL, customer_id VARCHAR(200),
 correlation_id VARCHAR(200) NOT NULL, payload JSONB NOT NULL
);
CREATE TABLE IF NOT EXISTS quarantined_events (
 id BIGSERIAL PRIMARY KEY, event_id UUID, reason TEXT NOT NULL, raw_payload JSONB NOT NULL, quarantined_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
