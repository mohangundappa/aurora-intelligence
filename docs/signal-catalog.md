# Signal catalog

Signal definitions are YAML-discovered from `classpath:/signals/*.yaml` or the
`AURORA_SIGNALS_LOCATION` override. A Spring calculator returns a numeric
snapshot, confidence, structured attributes, and explanation. Policy consumes
attributes/value/confidence/freshness; it never parses explanation text.

Unless stated otherwise, signal calculations read persisted events for one
session, require personalization consent (`SignalEngine.eligible` requires at
least one event with personalization consent), and persist to
`derived_signals`. Values are bounded to 0–100. The common confidence formula
is `min(0.99, 0.55 + min(0.4, evidenceCount*0.1))`; zero evidence is 0.55.
Expiry is parsed from each YAML duration.

| Signal | Tier | Lookback | Freshness | Expiry | Owner |
|---|---|---:|---:|---:|---|
| destination-intent | real-time | 30d | 15m | 24h | Customer Intelligence |
| family-travel-affinity | real-time | 30d | 30m | 24h | Customer Intelligence |
| resort-affinity | real-time | 30d | 30m | 24h | Customer Intelligence |
| business-travel-affinity | real-time | 30d | 30m | 24h | Customer Intelligence |
| amenity-preference | near-real-time | 30d | 1h | 48h | Customer Intelligence |
| booking-intent | real-time | 30d | 15m | 24h | Customer Intelligence |
| price-sensitivity | near-real-time | 30d | 1h | 48h | Customer Intelligence |
| abandonment-risk | real-time | 7d | 15m | 24h | Customer Intelligence |
| journey-stage | real-time | 90d | 15m | 24h | Customer Intelligence |
| weekend-getaway-affinity | real-time | 30d | 30m | 24h | Customer Intelligence |

## `destination-intent`

The lifecycle table above is authoritative for this definition's tier,
lookback, freshness and expiry.

**Business/user:** Destination marketing uses it for destination content and
the decision engine uses it for discovery or Miami experiences. **Inputs:**
`DESTINATION_SEARCHED` and recency. **Tier:** rule/near-real-time.
**Calculation:** `35 + evidence*20 + recency*25`, capped at 100, where evidence
is matching destination-search count and recency is a freshness factor.
**Output:** numeric 0–100; attribute `destination`; confidence is evidence
based. **Freshness/lookback:** YAML freshness window over session history.
**Explanation template/rendered example:** “A destination search for Miami
occurred 2 time(s); recency contributes 25 points.” **Owner:** Marketing
personalization. **Lifecycle:** definition default, transitionable through
Draft → Tested → Approved → Deployed → Retired.

## `family-travel-affinity`

**Business/user:** Family marketing uses it to select family inventory and
content. **Inputs:** `TRAVEL_PARTY_SELECTED` children count. **Tier:**
aggregation/near-real-time. **Calculation:** each party with children adds 55
plus recency contribution, capped at 100. **Output:** 0–100; attribute
`children=present|none`; confidence reflects party evidence. **Freshness:** YAML
window. **Explanation:** “A travel party with children was selected.” **Owner:**
Family marketing. **Lifecycle:** YAML default and persisted lifecycle status.

## `resort-affinity`

**Business/user:** Resort merchandising uses it to prioritize resort inventory.
**Inputs:** resort filters and property evidence. **Tier:** rule/near-real-time.
**Calculation:** `TextAffinityCalculator` counts configured resort evidence and
applies the shared affinity scoring support. **Output:** 0–100; structured
affinity attribute. **Freshness:** YAML window. **Explanation:** “Resort
inventory or filtering was explored.” **Owner:** Property merchandising.
**Lifecycle:** persisted signal lifecycle.

## `business-travel-affinity`

**Business/user:** Business-travel marketing uses it to distinguish business
inventory interest. **Inputs:** business filters/properties. **Tier:**
rule/near-real-time. **Calculation:** shared text-affinity calculator over
business evidence. **Output:** 0–100; affinity attribute. **Freshness:** YAML
window. **Explanation:** “Business-travel evidence was observed.” **Owner:**
Business travel marketing. **Lifecycle:** persisted signal lifecycle.

## `amenity-preference`

**Business/user:** Property/experience marketing uses it to promote a requested
amenity. **Inputs:** `FILTER_APPLIED` values. **Tier:** aggregation.
**Calculation:** configured amenity evidence is aggregated and scored; seeded
pool evidence produces 100 after repeated/strong filter evidence. **Output:**
0–100; attribute `amenity`. **Freshness:** YAML window. **Explanation:**
“Amenity and filter interactions were aggregated over the session.” **Owner:**
Property marketing. **Lifecycle:** persisted signal lifecycle.

## `booking-intent`

**Business/user:** Conversion marketing uses it for recovery and next-best
action. **Inputs:** property, room, rate and booking-start events. **Tier:**
model/near-real-time. **Calculation:** `ModelService` serves the deployed
baseline scoring model; the snapshot includes model version and feature
contributions. Seeded headline output is 100 with version 1.0. **Output:**
0–100 plus `modelVersion`; confidence is model evidence confidence.
**Freshness:** YAML window. **Explanation example:** “Score uses deployed
booking-intent version 1.0; feature contributions explain the result.
Contributions: {propertyViewed=54.0, roomViewed=18.0, rateViewed=18.0,
bookingStarted=18.0}.” **Owner:** Conversion marketing/data science.
**Lifecycle:** model and signal lifecycles are separately audited.

## `price-sensitivity`

**Business/user:** Revenue marketing uses it to tune rate/value messaging.
**Inputs:** rate/filter payloads and budget evidence. **Tier:** aggregation.
**Calculation:** budget evidence returns 80; otherwise evidence is multiplied by
15 and capped at 60. Seeded budget-filter evidence returns 80. **Output:** 0–80
in current calculator, freshness from YAML. **Explanation:** “Budget-oriented
rate or filter behavior was observed.” **Owner:** Revenue marketing.
**Lifecycle:** persisted signal lifecycle.

## `abandonment-risk`

**Business/user:** Lifecycle marketing uses it for recovery. **Inputs:**
`BOOKING_STARTED` without completion and abandonment evidence. **Tier:** rule.
**Calculation:** shared risk scoring identifies unfinished booking evidence.
Seeded headline output is 55. **Output:** 0–100; no business attribute.
**Freshness:** YAML window. **Explanation:** “A booking was started without a
completion event.” **Owner:** Lifecycle marketing. **Lifecycle:** persisted.

## `journey-stage`

**Business/user:** Marketing operations uses the stage for orchestration and
console narrative. **Inputs:** furthest observed funnel event. **Tier:** rule.
**Calculation:** maps the furthest event to a numeric stage and structured
`stage` enum. Seeded headline stage is `Abandoned`. **Output:** numeric stage
plus `stage`; freshness from YAML. **Explanation:** “Journey stage is derived
from the furthest observed funnel event: Abandoned.” **Owner:** Marketing
operations. **Lifecycle:** persisted.

## `weekend-getaway-affinity`

**Business/user:** Destination marketing uses it for short-break creative.
**Inputs:** `TRAVEL_DATES_SELECTED`. **Tier:** rule/near-real-time.
**Calculation:** `WeekendGetawayAffinityCalculator` detects weekend-oriented
dates and emits `tripType=weekend` when evidence exists, otherwise `none`.
The deterministic headline seed uses 2026-01-16 through 2026-01-18, a Friday
through Sunday short stay, so its verified value is 35 and `tripType=weekend`.
**Output:** numeric value and `tripType`;
freshness from YAML. **Explanation template:** “Weekend dates matched the
getaway pattern {evidenceCount} time(s).” **Owner:** Destination marketing.
**Lifecycle:** persisted. This is the worked example for adding a signal.

## Adding a signal end to end

1. Add one YAML definition under the configured signal location.
2. Add one Spring `SignalCalculator` whose `name()` matches the definition.
3. Return numeric value, confidence/evidence, attributes, and explanation.
4. Add focused calculator/policy tests and a seeded event if the demo needs
   evidence. Do not edit `SignalRegistry`; it discovers definitions and beans.
5. Run ingestion/replay and inspect `/api/signals/definitions`,
   `/api/signals/lifecycle`, and `/api/console/sessions/{sessionId}`.

`weekend-getaway-affinity` is this worked example: one YAML definition plus one
calculator class, with no central registry edit. The generic starter remains
`signals/signal-template.yaml`.

## Lifecycle state machine

```text
Draft → Tested
Tested → Draft | Approved
Approved → Tested | Deployed
Deployed → Approved | Retired
Retired → terminal
```

`POST /api/signals/lifecycle/{name}/{status}` validates transitions and writes
`signal_lifecycle_audit` with actor, action, old/new status, version, and time.
