# Event catalog

Aurora captures meaningful journey milestones rather than every DOM click. The
catalog supports destination intent, party composition, amenity preference,
booking progression, identity continuity, and experiment outcomes. A rendered
button or page transition is not automatically an event unless it changes a
customer state or provides evidence needed by a signal. Consent is classified
per event below; the current ingestion endpoint accepts the envelope and stores
its consent flags rather than rejecting an analytics-off event. Signal
calculators apply personalization consent to each event's evidence; a later
consented event does not retroactively personalize earlier denied evidence.

## Canonical envelope

| Property | Type | Required |
|---|---|---|
| `eventId` | UUID | yes, idempotency key |
| `eventName` | enum string | yes |
| `eventTime`, `receivedTime` | ISO-8601 timestamps | yes |
| `schemaVersion`, `source` | string | yes |
| `sessionId`, `anonymousId`, `correlationId` | string | yes |
| `customerId` | string | nullable |
| `consent.analytics`, `consent.personalization` | boolean | yes |
| `payload` | object | yes, event-specific |

## Event definitions

### `PAGE_VIEWED`

Meaning: a meaningful page impression for journey context and replay. Marketing
value: establishes journey entry without tracking every UI repaint. Producer:
Aurora web. Consumers: raw store and signals. Consent: analytics is required
for useful retention; personalization is not required. Required payload `path`
(string); optional `referrer` (string).

### `DESTINATION_SEARCHED`

Meaning: destination intent. Marketing value: a direct destination preference
for content and policy. Producer: search form. Consumers: destination-intent,
policy, funnel, console. Consent: analytics. Required `destination` (string);
optional `checkIn`, `checkOut` (string).

### `TRAVEL_DATES_SELECTED`

Meaning: selected stay dates. Marketing value: exposes trip patterns without
inferring them from a calendar render. Producer: search form. Consumer:
weekend-getaway-affinity. Consent: analytics. Required `checkIn`, `checkOut`
(ISO date strings).

### `TRAVEL_PARTY_SELECTED`

Meaning: adults and children. Marketing value: supports family content and
avoids demographic guessing. Producer: search form. Consumer:
family-travel-affinity. Consent: analytics. Required `adults`, `children`
(integers).

### `FILTER_APPLIED`

Meaning: deliberate result refinement. Marketing value: captures amenity,
resort, or budget preference without recording every result click. Producer:
results filters. Consumers: amenity, resort, price signals and funnel. Consent:
analytics. Required `filter`, `value` (strings).

### `PROPERTY_VIEWED`

Meaning: meaningful property consideration. Marketing value: distinguishes
research from search intent. Producer: results/property page. Consumers:
resort-affinity, booking model, funnel, console. Consent: analytics. Required
`propertyId` (string).

### `ROOM_VIEWED`

Meaning: room-level consideration. Marketing value: stronger booking evidence.
Producer: property page. Consumers: booking model and funnel. Consent:
analytics. Required `propertyId`, `roomId` (strings).

### `RATE_VIEWED`

Meaning: rate consideration. Marketing value: price and conversion evidence.
Producer: property page. Consumers: price signal, booking model, funnel.
Consent: analytics. Required `propertyId`, `roomId` (strings), `rate` (number).

### `BOOKING_STARTED`

Meaning: entry into booking. Marketing value: funnel denominator and intent
evidence. Producer: room selection. Consumers: booking model, abandonment,
experiments, funnel. Consent: analytics. Required `propertyId` (string).

### `BOOKING_ABANDONED`

Meaning: unfinished booking intent when the visibility handler or presenter
trigger fires. Marketing value: identifies recovery opportunity. Producer:
booking page. Consumers: abandonment-risk, journey-stage. Consent: analytics.
Required `propertyId`, `reason` (strings).

### `BOOKING_COMPLETED`

Meaning: simulated confirmation. Marketing value: outcome for funnel and
experiment joins. Producer: booking form. Consumers: experiment outcomes,
funnel, console. Consent: analytics. Required `propertyId`, `bookingId`
(strings). No payment is processed.

### `CUSTOMER_IDENTIFIED`

Meaning: explicit login/identification. Marketing value: joins anonymous
history without implicit identity guessing. Producer: simulated login.
Consumers: `IdentityStitcher`, profile merge, timeline and recalculation.
Consent: analytics; personalization remains independently checked. Required
`customerId` (string).

### `OFFER_PRESENTED`

Meaning: a decision-backed offer was shown. Marketing value: exposure
denominator. Producer: decision/site integration. Consumer: experiments.
Consent: personalization. Required `offerId` (string).

### `OFFER_CLICKED`

Meaning: deliberate offer engagement. Marketing value: intermediate experiment
outcome. Producer: site offer interaction. Consumer: experiments. Consent:
personalization. Required `offerId` (string).

## Complete sample envelope

```json
{
  "eventId": "9c7f1f3b-2f8e-4d41-a6b2-6d0a9e7b1c10",
  "eventName": "DESTINATION_SEARCHED",
  "eventTime": "2026-01-15T12:00:00Z",
  "receivedTime": "2026-01-15T12:00:01Z",
  "schemaVersion": "1.0",
  "source": "aurora-web",
  "sessionId": "demo-headline-miami",
  "anonymousId": "demo-anon-miami",
  "customerId": null,
  "correlationId": "demo-correlation-miami",
  "consent": {"analytics": true, "personalization": true},
  "payload": {
    "destination": "Miami",
    "checkIn": "2026-06-05",
    "checkOut": "2026-06-08"
  }
}
```

## Validation, quarantine, and evolution

`EventCatalog.validate` checks event names and required event-specific
properties/types. A destination search without `payload.destination` is
quarantined with reason `payload.destination is required for
DESTINATION_SEARCHED`. `quarantined_events` stores event ID, reason and
original JSON; the event is not published.

`POST /api/v1/events` accepts one envelope or a JSON array and returns accepted,
duplicate, and quarantined counts. Duplicate `eventId` values are ignored
idempotently. `schemaVersion` is persisted. Additive payload properties are
compatible; a breaking change needs a new schema version, catalog validation,
calculator/policy review, and migration tests.
