# Ingestion API

OpenAPI is served at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

`POST /api/v1/events` accepts one JSON envelope and returns accepted,
duplicate, and quarantined counts. Invalid payloads are retained with a reason.
`POST /api/v1/events/replay?sessionId=...` replays persisted raw events.

Example:

```json
{
  "eventId":"00000000-0000-0000-0000-000000000001",
  "eventName":"DESTINATION_SEARCHED",
  "eventTime":"2026-01-15T12:00:00Z",
  "receivedTime":"2026-01-15T12:00:00Z",
  "schemaVersion":"1.0","source":"demo",
  "sessionId":"demo-headline-miami","anonymousId":"demo-anon-miami",
  "customerId":null,"correlationId":"demo-correlation-miami",
  "consent":{"analytics":true,"personalization":true},
  "payload":{"destination":"Miami"}
}
```
