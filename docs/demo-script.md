# Demo script (10–15 minutes)

Run `./scripts/seed-demo.sh --reset`, then open the console. Seeded IDs are
fixed and synthetic; no wall-clock values are required.

1. **0:00–1:00 — context.** Explain that Aurora works beside a CDP. Show the
   site and console navigation.
2. **1:00–3:00 — headline journey.** Select session `demo-headline-miami`.
   Show raw `DESTINATION_SEARCHED`, party `2/2`, pool/resort filters, repeated
   property views, room/rate view, booking start, and abandonment.
3. **3:00–5:00 — why.** Point to destination, family, resort, amenity,
   price-sensitivity, booking-intent, and `Abandoned` journey-stage signals.
   Read each explanation and provenance, then show the family-resort action,
   reason codes, and correlation ID.
4. **5:00–6:30 — identity.** Select `demo-identity-stitch`, open the identity
   timeline, and show anonymous signals retained after the explicit login event.
   The login is simulated and does not silently merge identities.
5. **6:30–8:00 — lifecycle.** Open `/console/lifecycle`, deploy booking-intent
   2.0, inspect the changed prediction, then roll back to 1.0. Explain the
   audit trail and feature contributions.
6. **8:00–10:00 — measurement guardrail.** Open `/console/experiments` before
   seeded volume if demonstrating the insufficient-data state. The UI must
   withhold conversion claims below 30 subjects per variant.
7. **10:00–12:00 — populated experiment.** After the seed completes, show
   70 synthetic sessions, exposure/outcome joins by correlation ID, and the
   populated control/treatment comparison. Label it synthetic demo volume.
8. **12:00–13:00 — funnel and operations.** Open `/console/funnel` for
   aggregate stage counts or add `?session=demo-headline-miami`. Open
   `/console/ops`; call out quarantine reasons, freshness, latency, and the
   explicitly labelled persisted-timestamp lag approximation.
9. **13:00–15:00 — value.** Connect the reusable signal/model path to a target
   derived from the visible assumptions table. Do not describe it as a measured
   commercial result.
