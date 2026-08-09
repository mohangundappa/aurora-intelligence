# Signal catalog

All ten definitions are YAML-discovered. Calculators return numeric value,
confidence, structured attributes, and human explanation. Values below describe
the seeded/demo lifecycle state; lifecycle transitions are persisted.

| Signal | Meaning / logic | Tier / freshness |
|---|---|---|
| destination-intent | destination searches plus recency; `destination` attribute | RULE / 15m |
| family-travel-affinity | adults/children party composition; `children` attribute | AGGREGATION / 30m |
| resort-affinity | resort/property evidence; `affinity` attribute | RULE / 30m |
| business-travel-affinity | business property/filter evidence | RULE / 30m |
| amenity-preference | filter aggregation; `amenity` attribute | AGGREGATION / 1h |
| booking-intent | deployed baseline model and feature contributions; `modelVersion` | MODEL / 15m |
| price-sensitivity | rate/filter evidence | AGGREGATION / 1h |
| abandonment-risk | unfinished booking evidence | RULE / 15m |
| journey-stage | furthest funnel event; `stage` attribute | RULE / 15m |
| weekend-getaway-affinity | Friday/Saturday date evidence; `tripType` attribute | RULE / 30m |

Owner metadata lives in each YAML definition. Explanations are presentation
text only; policy conditions use attributes, values, confidence, and freshness.
