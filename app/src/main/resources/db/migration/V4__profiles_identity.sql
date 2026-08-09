CREATE TABLE IF NOT EXISTS cdp_profiles (
  anonymous_id VARCHAR(200) PRIMARY KEY,
  customer_id VARCHAR(200),
  loyalty_tier VARCHAR(80) NOT NULL DEFAULT 'Guest',
  loyalty_points INTEGER NOT NULL DEFAULT 0,
  analytics_consent BOOLEAN NOT NULL DEFAULT FALSE,
  personalization_consent BOOLEAN NOT NULL DEFAULT FALSE,
  attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
  audiences JSONB NOT NULL DEFAULT '[]'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS identity_links (
  id BIGSERIAL PRIMARY KEY,
  anonymous_id VARCHAR(200) NOT NULL,
  customer_id VARCHAR(200) NOT NULL,
  event_id UUID NOT NULL UNIQUE,
  linked_at TIMESTAMPTZ NOT NULL,
  link_method VARCHAR(80) NOT NULL,
  correlation_id VARCHAR(200) NOT NULL,
  absorbed_event_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS identity_links_customer_idx
  ON identity_links(customer_id, linked_at DESC);
