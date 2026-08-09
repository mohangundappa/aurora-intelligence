# Data flow

Three views of the same system, from coarse to specific. Every box names a real class,
endpoint, topic, or table in this repository, so the diagrams can be read against the code.

## 1. End-to-end data flow

How a browser action becomes a governed event, a signal, a decision, a rendered experience,
and finally a measured outcome.

```mermaid
flowchart TB
  subgraph site["Aurora Hotels site (Next.js)"]
    behavior["Visitor behavior<br/>search · filter · view · book · log in"]
    tracker["lib/tracker.ts<br/>allow-listed events only"]
    experience["Personalized experience<br/>OFFER_PRESENTED · OFFER_CLICKED"]
  end

  subgraph collect["Event collection (ingest)"]
    api["POST /api/v1/events<br/>single or batch"]
    validate["EventCatalog<br/>canonical + per-event schema"]
    raw[("raw_events<br/>idempotent on eventId")]
    quarantine[("quarantined_events<br/>reason retained")]
    topic{{"aurora.events.raw.v1<br/>Redpanda"}}
  end

  subgraph intel["Customer intelligence"]
    cdp["CdpAdapter → SimulatedCdpAdapter"]
    profiles[("cdp_profiles")]
    stitch["IdentityStitcher<br/>only on CUSTOMER_IDENTIFIED"]
    links[("identity_links<br/>method · when · absorbed count")]
    engine["SignalEngine<br/>calculator per YAML definition"]
    signals[("derived_signals<br/>value · confidence · expiry")]
    model["ModelService<br/>deployed booking-intent version"]
    context["ContextService<br/>session + history + consent"]
    redis[("Redis<br/>context cache")]
    decision["DecisionEngine<br/>decision-policy.yaml"]
    decisions[("decisions<br/>reason codes · correlationId")]
  end

  subgraph measure["Experimentation & measurement"]
    assign["ExperimentService<br/>SHA-256 on stable subject"]
    exposures[("experiment_exposures")]
    outcomes[("experiment_outcomes")]
    lift["Measurement<br/>lift, withheld under 30/arm"]
  end

  console["Marketing intelligence console<br/>/console · lifecycle · experiments · funnel · ops"]

  behavior --> tracker --> api --> validate
  validate -->|"valid"| raw
  validate -->|"invalid"| quarantine
  raw --> topic --> engine
  raw -.->|"POST /api/v1/events/replay"| engine
  raw --> cdp --> profiles
  raw --> stitch --> links
  profiles --> context
  links --> context
  engine --> signals --> context
  model --> context
  context <--> redis
  context --> decision
  decision --> decisions
  decision --> assign --> exposures
  decisions --> experience
  experience --> tracker
  outcomes --> lift
  exposures --> lift
  raw -->|"BOOKING_COMPLETED joined by correlationId"| outcomes
  signals --> console
  decisions --> console
  lift --> console
  quarantine --> console
  links --> console
```

Two properties worth reading off the diagram:

- **Collection is separate from calculation.** `raw_events` is the system of record; signals are
  derived from it, never in place of it. That is what makes the replay edge possible — the same
  events can be recomputed after a signal definition changes.
- **Nothing reaches the experience without a decision.** The site renders what
  `GET /api/sessions/{sessionId}/decision` returns, so the customer-facing change and the
  console's reason codes are the same object rather than two implementations of one rule.

## 2. Consent as a filter on the flow

Consent is evaluated **per event**, and per signal definition. A later `personalization: true`
event does not retroactively license evidence collected while consent was denied.

```mermaid
flowchart LR
  events["raw_events<br/>each with consent flags"]
  split{"per event:<br/>personalization granted?"}
  granted["Evidence usable for<br/>personalized signals"]
  denied["Retained for analytics<br/>never personalized"]
  defn{"per definition:<br/>consentRequired?"}
  computed["Signal computed"]
  withheld["Signal withheld<br/>console shows why"]
  safe["DecisionEngine<br/>STANDARD_WELCOME<br/>CONSENT_NOT_GRANTED · SAFE_DEFAULT"]

  events --> split
  split -->|"yes"| granted
  split -->|"no"| denied
  granted --> defn
  denied --> defn
  defn -->|"true → consented evidence only"| computed
  defn -->|"false → all evidence"| computed
  defn -->|"no eligible evidence"| withheld
  withheld --> safe
  computed --> safe
```

An event whose consent was denied is still collected and still auditable — it simply cannot
become a personalized signal. Denying consent therefore degrades to a safe default rather than
producing an empty screen with no explanation.

## 3. Decision → outcome, joined by correlation ID

This is the loop that answers "did the personalized action create incremental value?", and the
reason every decision carries a `correlationId` from the moment it is made.

```mermaid
sequenceDiagram
  participant V as Visitor
  participant S as Site
  participant D as DecisionEngine
  participant X as ExperimentService
  participant M as Measurement

  V->>S: Behavior (search Miami, family party, pool filter)
  S->>D: GET /api/sessions/{sessionId}/decision
  D->>X: Assign variant (deterministic, stable subject)
  X-->>D: control | treatment
  D-->>S: experience + reasonCodes + correlationId
  S->>S: Render experience, emit OFFER_PRESENTED (correlationId)
  Note over X: exposure recorded
  V->>S: Clicks offer, starts booking, completes booking
  S->>M: OFFER_CLICKED / BOOKING_STARTED / BOOKING_COMPLETED (same correlationId)
  Note over M: outcome joined to the decision that caused it
  M-->>M: Conversion by variant, absolute + relative lift
  M-->>V: Withheld until 30 exposed subjects per arm
```

The stable subject key stays the anonymous ID across identity stitching, so a visitor who logs
in mid-journey does not switch variants and invalidate their own exposure.

## Where the boundaries sit

The same flow, coloured by who owns each part in a real engagement — see
[ownership-boundaries.md](ownership-boundaries.md) for the full matrix.

| Stage in the flow | Owner in production |
| --- | --- |
| Event collection, profile store, identity resolution, audiences, consent record | CDP platform (Adobe, Salesforce, Tealium, Segment) |
| Schema configuration, source connections, destination setup | CDP implementation partner |
| Web properties, auth, booking systems, data platform hosting | Client IT |
| Signal framework, model lifecycle, context service, decisioning, measurement, console | Our customer-intelligence solution |
| Signal and offer definitions, experiment intent, reading the results | Marketing team |

In this MVP the first two rows are stood in for by `SimulatedCdpAdapter` behind the
provider-neutral `CdpAdapter` seam. It exists so the demo needs no commercial licence — it is
not a CDP replacement, and swapping in a real provider means implementing that one interface.
