UPDATE model_versions
SET status = 'DEPLOYED',
    deployed_at = COALESCE(deployed_at, now())
WHERE model_name = 'booking-intent'
  AND version = '1.0'
  AND status = 'APPROVED';
