---
name: testing-aurora-showcase
description: How to run and end-to-end test the Aurora Hotels customer-intelligence showcase (Next.js site + console on :3000, Spring Boot on :8080, Postgres/Redis/Redpanda via Docker Compose), including how to reach states the UI cannot produce.
---

# Testing the Aurora Hotels customer-intelligence showcase

## Bring the stack up
```bash
MAVEN_MIRROR_URL=https://repo.huaweicloud.com/repository/maven/ docker compose up --build -d
./scripts/seed-demo.sh --reset
```
Maven Central rate-limits the sandbox, so a rebuild without `MAVEN_MIRROR_URL` may fail.
Check `docker compose ps` first — the stack is often already running; reuse it.
Health: `curl localhost:8080/actuator/health`. Site `:3000`, console `:3000/console`.

## Login
Simulated only: `traveler@example.test` / `showcase` on `/login`.
Browser login produces customerId `demo-aurora-member`; the seeded API scenario
uses `demo-customer-100` — these are different journeys, don't confuse them.

## Selecting seeded journeys
The console dropdown is capped at ~50 sessions and may not list the named seed
scenarios. Navigate directly instead:
`/console?session=demo-headline-miami`, `/console?session=demo-identity-stitch`.

## Reaching states the UI cannot produce
- The site tracker hardcodes `consent {analytics:true, personalization:true}`
  (`frontend/lib/tracker.ts`), so consent-denied decisions are only reachable by
  POSTing to `/api/v1/events`.
- Every event envelope needs `eventId, eventName, eventTime, receivedTime,
  schemaVersion, source, sessionId, anonymousId, correlationId, consent{analytics,
  personalization}, payload`. Omitting `receivedTime` quarantines the event with
  "receivedTime is required" — an easy false negative when testing consent.
- Consent is read from the latest event, so one event with `personalization:true`
  re-personalizes the whole session.

## Caching
Context is cached in Redis for 30s and is NOT evicted by model deployment. After a
lifecycle deploy/rollback, wait >30s before re-reading `/console` or the served
score will look unchanged.

## Lifecycle UI gotchas
- The lifecycle page does not reliably re-render after a transition; press F5
  after each click or you will act on stale state.
- Model transitions have no legality check (unlike signal transitions), and
  approving the currently-deployed version leaves zero DEPLOYED versions, which
  makes `/api/console/sessions/{id}` return 500 and the console show
  "No journey is available yet". Recovery: click Deploy / rollback on 1.0, or
  `update model_versions set status='DEPLOYED' where model_name='booking-intent' and version='1.0';`
- Signal statuses have no "back to DRAFT" button; restore with
  `update signal_lifecycle set status='DRAFT' where signal_name=...`.

## Manipulating experiment data safely
```bash
docker compose exec -T postgres psql -U aurora -d aurora \
  -c "create table exposures_backup as select * from experiment_exposures;"
# ...mutate experiment_exposures...
docker compose exec -T postgres psql -U aurora -d aurora \
  -c "delete from experiment_exposures;" \
  -c "insert into experiment_exposures select * from exposures_backup;" \
  -c "drop table exposures_backup;"
```
Prefer this over `seed-demo.sh --reset`, which wipes browser-created evidence.

## Views worth checking
`/console` (journey), `/console/lifecycle`, `/console/experiments`,
`/console/funnel`, `/console/ops`. `/console/funnel` shows the *browser session*
funnel if `sessionStorage["aurora.session"]` exists; to see the aggregate funnel a
presenter would get on a fresh machine, open it in an incognito window.

## Devin Secrets Needed
None — login is simulated and no external credentials are used.
