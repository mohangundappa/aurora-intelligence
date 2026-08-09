# Architecture

Aurora is a modular Spring Boot monolith with explicit module boundaries and a
real Redpanda boundary. PostgreSQL is the replayable raw-event record; Redis is
a low-latency context cache with graceful PostgreSQL fallback. The CDP adapter
boundary is provider-neutral and the simulator is local-only.

## Context and responsibilities

- `ingest`: validates envelopes, quarantines invalid data, persists accepted
  events, publishes Redpanda messages, and supports replay.
- `identity`: handles explicit `CUSTOMER_IDENTIFIED` links and preserves
  anonymous history.
- `cdp`: provider adapter and durable simulated profile/context state.
- `signals`: discovers YAML definitions, resolves Spring calculator beans,
  computes structured attributes, and persists explanations/provenance.
- `decision`: evaluates the YAML decision policy over signals and consent,
  persists correlation-linked decisions.
- `models`: model versions, evaluation, approval/deployment/rollback, audit,
  explanations, and basic drift indicators.
- `experiments`: deterministic assignment and persisted exposure/outcome
  measurement with an insufficient-sample guard.
- `context`/`console-api`: context reads, journey directory, funnel, lifecycle,
  operations, and delivery-assumption views.

## Flows

Accepted event → PostgreSQL → Redpanda → signal consumer → derived signals →
context cache → policy decision. Context mutation events evict Redis. Replay
reads raw events and rebuilds derived signals. Identity stitching is explicit;
there is no implicit merge.

Real-time processing is event-driven. Batch/replay processing is initiated from
the raw-event record. The demo's consumer-lag metric is an approximation from
persisted timestamps, not native broker offset lag.

## Enterprise path

For high volume, partition raw events by tenant/time, scale consumers by
partition, move derived signal computation to independently scalable workers,
and use a managed PostgreSQL/Redpanda/Redis topology. The modular boundaries are
the extraction seams; the MVP is not claiming multi-region HA.

Consent is carried per event and personalization defaults safely off. Production
work must add tenant isolation, encryption/key management, retention/deletion
workflows, access control, audit export, and provider-specific consent mapping.
