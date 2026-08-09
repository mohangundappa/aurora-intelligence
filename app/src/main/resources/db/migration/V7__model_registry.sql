CREATE TABLE IF NOT EXISTS model_versions (
  id BIGSERIAL PRIMARY KEY,
  model_name VARCHAR(120) NOT NULL,
  version VARCHAR(40) NOT NULL,
  status VARCHAR(40) NOT NULL,
  features JSONB NOT NULL,
  weights JSONB NOT NULL,
  bias NUMERIC(10,4) NOT NULL,
  deployed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(model_name, version)
);

CREATE TABLE IF NOT EXISTS model_audit (
  id BIGSERIAL PRIMARY KEY,
  model_name VARCHAR(120) NOT NULL,
  version VARCHAR(40) NOT NULL,
  action VARCHAR(40) NOT NULL,
  actor VARCHAR(120) NOT NULL,
  from_version VARCHAR(40),
  to_version VARCHAR(40),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO model_versions(model_name,version,status,features,weights,bias)
VALUES
 ('booking-intent','1.0','APPROVED','["propertyViewed","roomViewed","rateViewed","bookingStarted"]'::jsonb,
  '{"propertyViewed":18,"roomViewed":18,"rateViewed":18,"bookingStarted":18}'::jsonb,28),
 ('booking-intent','2.0','TESTED','["propertyViewed","roomViewed","rateViewed","bookingStarted"]'::jsonb,
  '{"propertyViewed":12,"roomViewed":24,"rateViewed":24,"bookingStarted":28}'::jsonb,18)
ON CONFLICT (model_name,version) DO NOTHING;
