# Enterprise roadmap

The MVP is intentionally honest about what is simulated. The local stack
demonstrates boundaries and durable behavior; it does not claim production
scale, provider parity, or commercial lift.

## MVP versus production

| Area | MVP today | Production change |
|---|---|---|
| Event ingestion | Spring endpoint, validation, PostgreSQL raw record, Redpanda publication, quarantine | Managed ingress, authentication, schema registry, tenant quotas, partitioning, DLQ operations |
| Replay | Session-scoped raw-event republish | Governed replay jobs, backfill windows, idempotent batch orchestration and audit |
| CDP and MarTech hand-off | PostgreSQL simulator behind `CdpAdapter`, plus provider-neutral audience, offer-delivery, and campaign-registration seams | Provider adapters, authentication, provider SLAs, consent/identity contract tests, rate-limit and asynchronous retry handling |
| Identity | Explicit simulated `CUSTOMER_IDENTIFIED`; anonymous history retained | Provider namespace/graph mapping, merge/unmerge governance, deletion propagation |
| Signals | YAML + Spring calculator, persisted snapshot/explanation | Versioned registry service, independent workers, lineage, owner approval and SLOs |
| Models | Baseline scoring, offline evaluation, deploy/rollback, basic drift indicator | Feature store, signed artifacts, canary/shadow deployment, real drift/quality monitoring |
| Decisioning | YAML priority policy and safe default | Policy governance, suppression/contact service, offer inventory, multi-channel activation |
| Experiments | Hash assignment, exposure/outcome persistence, 30-subject guard | Statistical design service, warehouse, power analysis, SRM checks, privacy-safe reporting |
| Cache | Redis TTL/cache with PostgreSQL fallback and event eviction | HA Redis, invalidation delivery guarantees, cache warming and tenant isolation |
| Operations | SQL-derived metrics and health endpoints | Native broker lag, tracing, SLOs, alerting, incident/runbook integration |
| Security | Local credentials and consent fields; governance actors are self-declared and unverified in this showcase | SSO/RBAC, secrets manager, encryption, retention/deletion, masking and audit export |
| Digital Workforce | Read-only console, deterministic agents, evidence/refusal records, governed proposal lifecycle, provider-neutral simulated activation, measurement guard, and evaluation harness | Authenticated governance, live LLM runtime, real provider adapters, production experiment deployment and operating controls |

## CDP integration approach

All integrations implement `CdpAdapter` operations used by context and identity
code. The exact API, identity namespace behavior, consent purpose model,
activation latency and batch limits require confirmation against the client's
licensed product/edition.

| Provider | Honest integration focus | Requires provider confirmation |
|---|---|---|
| Adobe Experience Platform | Map profiles, identity namespaces, consent and audiences to Adobe APIs/streams; keep Aurora signals/decisions adjacent. | Which AEP product/API is licensed, streaming availability, namespace merge semantics and activation latency. |
| Salesforce Data Cloud | Map unified profiles, calculated insights/audiences and consent fields through the supported Data Cloud API/event path. | Tenant API limits, identity resolution rules, real-time profile freshness and activation products. |
| Tealium | Map audience/profile reads and event collection/activation connectors through the client's Tealium setup. | Exact AudienceStream/ CDP edition, visitor stitching rules, consent implementation and connector timing. |
| Segment | Map profile/traits, identity calls and destinations through Segment's supported APIs; keep decision policy in Aurora unless the client chooses activation there. | Workspace/source configuration, identity merge behavior, destination delivery guarantees and profile API limits. |

The implementation partner owns mapping and contract tests. The CDP vendor owns
provider behavior. Client IT owns credentials and production operations.

### MarTech activation seam

The showcase now exercises `AudienceActivation`, `OfferDelivery`, and
`CampaignRegistration` through deterministic simulated implementations. Their
shared contract includes destination identity, an opaque payload, an
idempotency key, accepted/rejected/partial outcomes, explicit rejection
reasons, counts, and opaque provider metadata. Repeating an idempotency key
returns the original result rather than double-activating.

Aurora enforces consent and eligibility before subject-level offer delivery;
the CDP or marketing platform remains authoritative for production consent,
suppression, identity, destination delivery, and campaign operations. Human
approval still gates experiment activation. No agent or provider adapter can
approve, deploy, retire, or mutate decision policy.

The simulator is not evidence of production integration parity. It does not
prove authentication, authorization, rate limits, asynchronous processing,
provider-specific quirks, data residency, SDK/API contract drift, or vendor
failure semantics. An implementation partner must build provider adapters and
contract tests against the client's licensed Adobe, Salesforce, Tealium,
Segment, or other platform.

## High-volume path

For an initial 1,000 events/second pilot, measure PostgreSQL write latency,
connection-pool saturation, Redpanda publish/consume throughput, signal
recalculation cost and Redis hit rate before changing topology. At roughly
10,000 events/second, partition raw events by tenant/time, use multiple broker
partitions and consumers, and move heavy calculations off the context read
path. At higher volume, use a warehouse for funnel/experiment aggregates and a
dedicated low-latency serving store for profile/signal reads. These are sizing
starting points, not load-test results from this repository.

## Multi-tenancy, governance, and operations

Add tenant ID to every envelope/table/index, enforce tenant-scoped authorization,
separate keys and quotas, and define regional retention. Require signal owners,
policy approvals, model promotion gates, experiment review, schema compatibility
checks, and audit retention. Integrate traces across event ID, correlation ID,
decision ID and provider request ID. Define incident ownership across client IT,
CDP partner/vendor, and the Aurora application team before production rollout.

The local governance endpoints accept a caller-supplied actor for demonstration
and record it as `SELF_DECLARED_UNVERIFIED`; this is attribution, not
authentication or authorization. Production must authenticate and authorize
approvals and other governance transitions with SSO/RBAC before relying on the
approval gate.

## Known showcase gaps

1. Governance endpoints have no authentication or authorization; actors are
   self-declared and unverified.
2. The console can render an unmet-analysis guard defensively, but the product
   Analytics Agent currently refuses before persisting an insufficient-sample
   analysis, so that branch is not product-reachable.
3. No live LLM runtime exists behind `AgentRuntime`; the showcase runtimes are
   deterministic.
4. No real CDP or MarTech provider adapters exist; local implementations
   exercise provider-neutral seams and explicit outcomes only.

These are engagement work, not claims hidden behind the demo: the client
provides authenticated governance, licensed provider contracts, production
data, and operating requirements that an implementation must satisfy.
