# Presentable demo script: 10–15 minutes

## Before the room

Prerequisites: Docker Compose, Java 21/Maven 3.6+, Node 20 and Chromium.
From a clean checkout run exactly:

```bash
MAVEN_MIRROR_URL=https://repo.huaweicloud.com/repository/maven/ \
  docker compose up --build -d
./scripts/seed-demo.sh --reset
```

Open `http://localhost:3000`, then keep the console in another tab. The seed
uses fixed IDs/timestamps and creates synthetic experiment volume. To reset,
rerun `./scripts/seed-demo.sh --reset`.

## Script

### 0:00–1:00 — frame the problem

Say: “Aurora works beside a CDP. The CDP remains the profile, consent,
identity, audience and activation platform; Aurora accelerates signals, models,
decisions and measurement.”

Point to the site header and `/console` navigation. Business value: one
explainable path from meaningful behavior to action, rather than a collection
of mock screens.

### 1:00–3:30 — create the journey

Use the seeded `demo-headline-miami` session for deterministic console proof,
then optionally reproduce the site interaction:

1. Enter `Miami`.
2. Set check-in `2026-06-05`, check-out `2026-06-08`.
3. Select Adults `2`, Children `2`.
4. Click **Search stays**.
5. Check **Pool** and **Resort**.
6. Open the first property, click **See room details**, then **Select room**.
7. Click **Trigger presenter abandonment event**.

Expected events in order include `DESTINATION_SEARCHED`,
`TRAVEL_DATES_SELECTED`, `TRAVEL_PARTY_SELECTED`, `FILTER_APPLIED` for pool
and resort, `PROPERTY_VIEWED`, `ROOM_VIEWED`, `RATE_VIEWED`,
`BOOKING_STARTED`, and `BOOKING_ABANDONED`. The deterministic seeded session
also contains repeated property, destination and party evidence to make the
values stable. The seed contains budget evidence for the price-sensitivity
calculator, but there is no budget control in the customer UI and the presenter
does not click one.

### 3:30–5:30 — explain the why

Select `demo-headline-miami` in the console. In **Derived signals**, point to
the explanation and provenance, not just the score. Verified seeded values are:

```text
destination-intent       75
family-travel-affinity  100
amenity-preference      100
resort-affinity          75
price-sensitivity        80
booking-intent           100
abandonment-risk          55
journey-stage             Abandoned
weekend-getaway-affinity   35
```

Point to **NBA and reason codes**. Expected decision:
`RECOMMEND_FAMILY_RESORT` / `FAMILY_RESORT_RECOMMENDATION`, with
`FAMILY_RESORT_EVIDENCE` and `RESORT_AFFINITY_ELIGIBLE`. Business value:
Marketing can explain why a family resort was recommended and audit the
correlation ID back to events.

### 5:30–7:00 — identity moment

Select `demo-identity-stitch`, or reproduce it by opening `/login` in the same
browser session and submitting the prefilled simulated credentials. The exact
event is `CUSTOMER_IDENTIFIED` with `customerId=demo-aurora-member` for the
browser flow, while the seeded API scenario uses `demo-customer-100`.

Open the identity timeline. Explain that the link is explicit, anonymous
signals remain pre-identification history, and no implicit merge occurs.
Business value: continuity without pretending the accelerator owns the CDP's
identity graph.

### 7:00–8:30 — rollout accelerator

Open `/console/lifecycle`. Deploy booking-intent `2.0`, inspect the prediction
version and per-feature contributions, then roll back to `1.0`. Point to the
audit entry. Business value: a repeatable path from evaluated version to
approved deployment and reversible serving behavior.

### 8:30–10:30 — guarded measurement

Open `/console/experiments`. Explain that a no-seed or small-data state shows
the prominent warning requiring 30 exposed subjects per variant and withholds
lift/significance claims.

After `seed-demo.sh --reset`, the deterministic 100-session seed typically
produces more than 30 exposures per arm. A clean reset seed verified 56 control
and 45 treatment exposures; an interactive walkthrough can add rows (the
observed live state was 59 control and 47 treatment). Outcome rows are
`OFFER_CLICKED`, `BOOKING_STARTED`,
and `BOOKING_COMPLETED`, joined to decisions through `correlationId`. Say
“synthetic demo volume,” not “commercial lift.”

To show a completion through the UI, open `/booking/aurora-miami?room=family-suite`,
fill Demo / Traveler / `traveler@example.test`, and click **Confirm simulated
booking**. The page shows a simulated confirmation; no payment is taken.

### 10:30–12:00 — funnel and operations

Open `/console/funnel`. Show aggregate distinct-session stages and drop-off;
append `?session=demo-headline-miami` for the single journey. Open
`/console/ops` and point to ingest/quarantine counts, reason breakdown,
freshness, decision latency, and **consumer lag approximation (persisted
timestamps)**.

### 12:00–14:00 — value and close

Open the delivery assumptions table. Explain that its computed target is based
on visible assumptions and is not a measured commercial result. Tie the three
business problems together: reusable definitions and rollback shorten
development/rollout; event-to-decision context shortens signal-to-decision
time; persisted exposure/outcome joins measure incremental value honestly.

## Troubleshooting

- Backend unhealthy: `docker compose ps`, then inspect
  `docker compose logs --tail=100 backend`.
- Maven HTTP 429 during build: rerun with the documented
  `MAVEN_MIRROR_URL`; do not commit that mirror.
- Empty console: wait for `/actuator/health`, rerun the seed, then select a
  backend session rather than “This browser session.”
- Missing signal evidence: use `./scripts/seed-demo.sh --reset`; consumer
  processing is near-real-time and context reads recalculate on cache miss.
- Reproduce only the guard: reset and create fewer than 30 exposures per arm;
  never delete measurement rows merely to force a headline.
