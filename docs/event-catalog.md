# Event catalog

All events use the canonical envelope: IDs, event/receive times, schema
version, source, session, anonymous/customer IDs, correlation ID, consent, and
typed payload. Analytics consent controls storage; personalization consent
controls personalized decisioning.

| Event | Required payload | Producers/consumers |
|---|---|---|
| PAGE_VIEWED | `path` | site → raw record/signals |
| DESTINATION_SEARCHED | `destination` | site → intent/funnel |
| TRAVEL_DATES_SELECTED | `checkIn`, `checkOut` | site → getaway signal |
| TRAVEL_PARTY_SELECTED | `adults`, `children` | site → family signal |
| FILTER_APPLIED | `filter`, `value` | site → amenity signal |
| PROPERTY_VIEWED | `propertyId` | site → funnel/model |
| ROOM_VIEWED | `propertyId`, `roomId` | site → funnel/model |
| RATE_VIEWED | `propertyId`, `roomId`, `rate` | site → price/model |
| BOOKING_STARTED | `propertyId` | site → experiment outcome/funnel |
| BOOKING_ABANDONED | `propertyId`, `reason` | site → abandonment/funnel |
| BOOKING_COMPLETED | `propertyId`, `bookingId` | site → experiment outcome/funnel |
| CUSTOMER_IDENTIFIED | `customerId` | login → identity stitch |
| OFFER_PRESENTED | `offerId` | decision/site → exposure context |
| OFFER_CLICKED | `offerId` | site → experiment outcome |

Example payload:

```json
{"eventName":"DESTINATION_SEARCHED","sessionId":"demo-headline-miami",
 "anonymousId":"demo-anon-miami","correlationId":"demo-correlation-miami",
 "payload":{"destination":"Miami"}}
```
