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
- **Model transitions are now legality-checked.** A fresh reset leaves
  `booking-intent` 1.0 DEPLOYED / 2.0 TESTED, and clicking "Deploy / rollback"
  on a TESTED version returns **409** (`Aurora API request failed: 409`) with no
  state change. The demo path is **Approve first, then Deploy / rollback**. If a
  click appears to do nothing, check `model_audit` — an empty table means the
  transition never happened rather than that the UI failed to refresh.
- **Every successful model transition may still render a raw error banner**
  (`Failed to execute 'json' on 'Response': Unexpected end of JSON input`) because
  the transition endpoints return an empty body while the client parses JSON. The
  transition itself succeeds — verify the status pills / `model_audit`, not the
  banner. This is presenter-visible; flag it rather than trusting the banner.
- Deploying a model does not evict the Redis context cache: wait >30s (TTL) before
  reloading a console session, or the old score persists. Useful clean signal to
  demo on a low-event session (e.g. `demo-identity-stitch`): booking-intent reads
  **28** under 1.0 (bias 28) and **18** under 2.0 (bias 18).
- Approving the currently-deployed version can leave zero DEPLOYED versions, which
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
`/console` (journey), `/console/workforce`, `/console/lifecycle`,
`/console/experiments`, `/console/funnel`, `/console/ops`. `/console/funnel` shows
the *browser session* funnel if `sessionStorage["aurora.session"]` exists; to see
the aggregate funnel a presenter would get on a fresh machine, open it in an
incognito window.

## Testing `/console/workforce` (digital workforce loop)
One aggregation endpoint backs the whole page: `GET /api/console/workforce`. It has
no per-objective error isolation, so any single bad row blanks the entire view.

- **A proposal that is not ACTIVATED can take the page down.** The analyses lookup
  resolves the experiment definition, which only exists after activation, so any
  proposal in PROPOSED/APPROVED/REJECTED may make the endpoint return 500 and the
  page show only `Aurora API request failed: 500`. Reproduce/clean up with:
  ```bash
  docker compose exec -T postgres psql -U aurora -d aurora -c "insert into experiment_proposals (...) select gen_random_uuid(),'<objective>',insight_id,'probe-experiment',... ,'PROPOSED',now(),... from marketing_insights limit 1;"
  docker compose exec -T postgres psql -U aurora -d aurora -c "delete from experiment_proposals where experiment_id='probe-experiment';"
  ```
- **Loading vs failure states** are easy to capture without code changes:
  `docker compose pause backend` then reload ⇒ "Loading workforce data" card;
  `docker compose stop backend` then reload ⇒ error banner. `docker compose start
  backend` recovers in a few seconds.
- **The analytics agent refuses instead of persisting a weak analysis.**
  `AnalyticsAgent` (min 30 exposures/arm) returns `ZERO_EXPOSURES`,
  `INSUFFICIENT_SAMPLE` or `INSUFFICIENT_VARIANTS` and writes no
  `experiment_analyses` row. To exercise the UI's "GUARD NOT MET" branch you must
  insert an analysis row with `sufficient_sample=false`, null `absolute_lift` and
  `relative_lift`, and non-zero `conversionRate` values inside `variant_results`
  (that last part is what proves the UI suppresses unsupported numbers).
- **Agent-proposed experiments cannot record exposures.** Generated variant names
  run 41–45 chars while `experiment_exposures.variant` is `varchar(40)`, so
  inserting exposures fails with "value too long". Workaround: repoint the
  proposal's `experiment_id` at a seeded experiment (e.g.
  `destination-experience-v1`) before running the analytics agent, and disclose it.
- **Read-only check without devtools noise:** the page should contain zero
  `button`/`input`/`select`/`form` elements; a Tab sweep should only ever land on
  links and `<details>` summaries.
- Refusals are rendered from the execution's `output` JSON (`code` + `reason`), but
  the status pill is driven by the execution status, which is `SUCCEEDED` for a
  refusal — expect that mismatch and check whether it has been fixed.
- An execution with `output: null` renders the literal text `null` in its
  disclosure; worth checking after any change to the executions section.

## Recording a client-ready walkthrough
- Running order that works well: site journey → `/console` (events, signals, NBA)
  → `/console?session=demo-identity-stitch` (identity timeline) →
  `/console/lifecycle` (approve, deploy, roll back) → `/console/experiments` →
  `/console/workforce` (loop, then the refusal objective).
- `/console/workforce` renders a global **Provider activation attempts** list
  *before* the objective cards. On a fresh seed that is ~109 rows of raw JSON, so
  the page opens on ~10 screens of noise. Scroll past it (or jump with `End` then
  scroll up) before narrating; expect to flag it as a presentation defect.
- The refusal objective (`demo-workforce-refusal`) renders **above** the complete
  loop objective (`demo-workforce-miami`) — order objective cards by intent, not
  by scroll position.
- **Do not trust the numbers in `docs/demo-script.md`.** The workforce experiment
  ID gets a random suffix on every reset and assignment is a hash of
  `subject + ":" + experimentId`, so the control/personalized split varies per
  reset (seen: 45/55 rather than the documented 50/50, giving 11.1% vs 12.7%
  instead of 10.0% vs 14.0%). Read the on-screen values and report the mismatch.
- A stale login persists in browser storage across `--reset`, so "This browser
  session" can show a named member (`demo-aurora-member`, Aurora Circle) while the
  identity timeline correctly says "No identity link has been recorded." Clear
  site data first if you want a genuinely anonymous opening.

## Regression sweep for the customer journey
Site: `/` search Miami → dates → 2 adults / 2 children → `/results` (a
"Recommended for this journey" decision card and an `OFFER_PRESENTED` event should
appear) → Pool filter → property → See room details → Select room → fill the form →
Confirm simulated booking → "See you at …". Then `/console` for
"This browser session" should list `PAGE_VIEWED … BOOKING_COMPLETED` and every
signal with value + confidence + freshness + explanation + provenance. Offer
delivery attempts are session-scoped; verify with:
```bash
docker compose exec -T postgres psql -U aurora -d aurora \
  -c "select operation,status,idempotency_key,context_id from martech_activation_attempts order by attempted_at desc limit 5;"
```
Keys look like `offer:{sessionId}:{action}:{experience}:{experimentId}`, so a new
browser session must produce its own key rather than reusing another visitor's.

## Devin Secrets Needed
None — login is simulated and no external credentials are used.
