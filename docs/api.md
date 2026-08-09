# API guide

The generated OpenAPI document is at `/v3/api-docs`; Swagger UI is at
`/swagger-ui.html`. The checked-in ingestion contract is
`docs/openapi.yaml`. All examples use the local default `http://localhost:8080`.

## Ingestion and replay

`POST /api/v1/events` accepts one envelope or an array:

```bash
curl -X POST http://localhost:8080/api/v1/events \
  -H 'content-type: application/json' \
  -d '{"eventId":"00000000-0000-0000-0000-000000000001","eventName":"DESTINATION_SEARCHED","eventTime":"2026-01-15T12:00:00Z","receivedTime":"2026-01-15T12:00:00Z","schemaVersion":"1.0","source":"demo","sessionId":"s1","anonymousId":"a1","customerId":null,"correlationId":"c1","consent":{"analytics":true,"personalization":true},"payload":{"destination":"Miami"}}'
```

Response:

```json
{"accepted":1,"duplicates":0,"quarantined":0,"acceptedEventIds":["..."],"quarantinedEventIds":[]}
```

Arrays return aggregate counts. Repeating the same `eventId` returns
`duplicates: 1` and does not publish a second broker message. Invalid events
return `quarantined: 1`; the original JSON and reason remain in
`quarantined_events`. Example reason:
`payload.destination is required for DESTINATION_SEARCHED`.

`POST /api/v1/events/replay?sessionId=demo-headline-miami` republishes persisted
events and returns `{"sessionId":"...","replayed":11}`. Replay is intentionally
idempotent at raw storage; consumers must retain their own idempotency behavior.

## Context and decisions

```text
GET /api/sessions/{sessionId}/journey
GET /api/sessions/{sessionId}/decision
GET /api/customers/{customerId}/context
GET /api/customers/{customerId}/signals
GET /api/signals/definitions
```

The journey response includes profile, recent behaviors, active signals and
decision. A signal contains numeric value, confidence, computed/expiry times,
explanation, provenance and structured attributes. A decision includes action,
experience, channel, reason codes, decision version, experiment ID,
explanation, session ID and correlation ID. If personalization consent is
absent, the decision is `STANDARD_WELCOME` with `CONSENT_NOT_GRANTED` and
`SAFE_DEFAULT`.

## Signal and model lifecycle

```text
GET  /api/signals/lifecycle
GET  /api/signals/lifecycle/{name}/audit
POST /api/signals/lifecycle/{name}/{status}?actor=presenter
GET  /api/models/{name}
GET  /api/models/{name}/audit
GET  /api/models/{name}/{version}/evaluation
POST /api/models/{name}/{version}/approve
POST /api/models/{name}/{version}/deploy
POST /api/models/{name}/{version}/rollback
POST /api/models/{name}/predict
```

Lifecycle transitions return the new row and append an audit record. An unknown
status is `400`; an illegal transition is `409`. Model prediction returns model
version, score and per-feature contributions.

## Experiments and identity

```text
GET /api/experiments/{experimentId}/performance
GET /api/identity/{anonymousId}/timeline
```

Performance returns control/treatment exposure, click, booking-start and
completion counts, conversion rates, and the sample guard. Below 30 exposed
subjects per arm, lift/significance is withheld and `insufficientSample` is
true. Identity timeline returns anonymous ID, customer ID, link source,
correlation ID and timestamp.

## Console

```text
GET /api/console/sessions
GET /api/console/sessions/{sessionId}
GET /api/console/funnel
GET /api/console/funnel/{sessionId}
GET /api/console/ops
GET /api/console/delivery
```

The funnel returns aggregate distinct-session stage counts and drop-off; a
session path filters to one journey. Operations returns ingest/quarantine
counts/reasons, decision latency, freshness distribution, component health,
and explicitly approximate persisted-timestamp lag. Delivery returns the
assumption rows and computed reduction label.

## Errors and operational semantics

Malformed JSON/event IDs or missing required properties are quarantined rather
than treated as accepted events. Duplicate IDs are non-errors and are reported
in the partial-success result. Controller failures return standard Spring HTTP
error JSON. Redis failures fall back to PostgreSQL context reads. Kafka/Redpanda
is distribution; PostgreSQL is the replayable source of truth.
