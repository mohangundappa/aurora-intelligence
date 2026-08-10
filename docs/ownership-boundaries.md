# Ownership boundaries

Aurora is an intelligence layer and accelerator that works with a CDP. It
never replaces Adobe Experience Platform, Salesforce Data Cloud, Tealium,
Segment, or the client's chosen profile/audience/activation platform. The
simulator implements the same local adapter seam so the demo runs without a
commercial CDP licence.

## Capability boundary

| Capability | CDP platform | Implementation partner | Aurora solution | Client IT | Marketing |
|---|---|---|---|---|---|
| Profile and audience system of record | Owns | Maps/configures | Reads through adapter | Operates access | Defines use |
| Provider identity graph | Owns provider semantics | Configures mappings/policy | Handles explicit demo stitch seam | Approves identity policy | Approves business identity use |
| Consent/purpose enforcement | Owns platform controls | Maps consent fields | Carries consent and safe decision default | Governs privacy/security | Defines permissible personalization |
| Raw event ingestion/replay | May provide source feeds | Implements connectors | Validates, quarantines, persists, replays | Runs infrastructure | Defines meaningful events |
| Signals and model rollout | May consume outputs | Integrates deployment process | YAML calculators, model registry, audit | Operates release controls | Owns definitions/acceptance |
| Decision policy/NBA | May activate decisions | Implements integration | Evaluates configured policy and persists explanation | Secures endpoint | Owns eligibility/suppression/offer rules |
| Experiment measurement | May provide activation | Aligns IDs/outcomes | Assignment, exposure/outcome join, guard | Owns data access | Owns hypothesis and interpretation |
| Production operations | Provider SLA | Delivery support | Application observability/runbooks | Platform, network, incident ownership | Business escalation |

The CDP remains authoritative for customer profile, consent, identity and
audience capabilities in a production integration. Aurora builds the signal,
model, decision and measurement acceleration around those capabilities.

## Party responsibilities

### CDP platform vendor

Provides the commercial profile, consent, identity, audience, activation and
provider API behavior under its contract. Its exact real-time/batch behavior,
identity resolution semantics, retention, and consent model require vendor
confirmation for each tenant and product edition.

### CDP implementation partner

Maps Aurora's `CdpAdapter` contract to provider APIs, configures schemas,
identity namespaces, audiences, consent purposes, credentials, rate limits and
activation destinations, and validates parity between provider and Aurora
views. It does not own Marketing's business policy.

### Client IT

Owns environments, networking, secrets, IAM, data retention/deletion,
monitoring integration, release approvals, disaster recovery, and vendor
contracts. Client IT operates the production CDP and Aurora runtime according
to the agreed service model.

### Our customer-intelligence team

Owns the reusable event, signal, model, decision and experiment accelerator;
the adapter boundary; tests; migration support; observability instrumentation;
and rollout enablement. We do not claim ownership of the CDP's system-of-record
functions.

### Marketing team

Owns event meaning, signal definitions, offer eligibility, suppression/contact
rules, reason-code language, experiment hypotheses, sample-size interpretation,
and approval of customer-facing experiences. Marketing cannot deploy a
production policy without the agreed governance process.

The local approval endpoint's actor field is self-declared and unverified; its
audit entry is attribution only, not proof that the caller was authorized.
Production must authenticate and authorize governance endpoints (for example
through client IT's SSO/RBAC) before treating human approval as an authorization
gate. That authentication mechanism is intentionally outside this showcase.

## Provider-neutral MarTech hand-off

Aurora uses provider-neutral contracts for the capabilities it hands to a
marketing platform:

| Interface | CDP/marketing platform provides | Implementation partner configures | Aurora adds | Simulator behavior |
|---|---|---|---|---|
| `AudienceActivation` | Audience destination, consent/purpose enforcement, delivery semantics | Audience schema, identity namespace, destination and eligibility mapping | Governed audience definition and idempotent hand-off | Deterministic accepted, rejected, or partial result |
| `OfferDelivery` | Channel/content destination, suppression and delivery controls | Payload mapping, channel credentials and provider contract tests | Consent-gated decision hand-off, correlation and reason codes | Deterministic delivery result with opaque provider metadata |
| `CampaignRegistration` | Campaign/journey registration and activation lifecycle | Campaign schema, destination mapping and operational limits | Human-approved experiment artifact registration | Deterministic idempotent registration result |

Each request carries a destination identity, an opaque payload contract and an
idempotency key. Each result carries accepted/rejected/partial status, counts,
an explicit rejection reason where applicable, and opaque provider metadata.
Aurora never treats the simulated metadata as a vendor contract.

Consent and eligibility are checked before a subject-level request reaches
`OfferDelivery`; a denied decision remains the safe default and does not call
the interface. Approved experiment activation remains a human governance
transition. These interfaces do not grant an agent authority to approve,
deploy, retire, or mutate a decision policy.

The simulated implementations prove only that Aurora's adapter seam, payload
shape, idempotency behavior, and failure representation are exercised locally.
They do not prove provider authentication, rate limits, asynchronous delivery
semantics, provider quirks, data residency, or contract stability over time.

## Migration/rollout RACI

| Activity | CDP vendor | Partner | Our team | Client IT | Marketing |
|---|---|---|---|---|---|
| Provider capability confirmation | A/R | C | C | C | I |
| Adapter mapping and contract tests | C | A/R | R | C | I |
| Consent and identity design | C | R | R | A | A |
| Signal/policy definition | I | C | R | C | A |
| Test data and shadow comparison | C | R | R | A | A |
| Production deployment | C | R | R | A | I |
| Experiment approval | I | C | R | C | A |

## Steady-state hand-offs

Provider API/identity/consent changes flow from the CDP vendor to the partner
and client IT, then into adapter contract tests. Signal/model/policy changes
flow from Marketing to our team for implementation and tests, then through
client IT release controls. Incidents cross the boundary with correlation IDs,
raw-event IDs, decision IDs, and provider request IDs where available.

## Explicit MVP boundary

The simulator persists profiles, identity links, audiences, consent and
attributes in PostgreSQL. Login and booking are simulated. Production must
replace it through `CdpAdapter` and must not infer that the local tables are a
replacement CDP.

## Digital Workforce boundary

The workforce console is a read-only presentation and audit surface. Aurora
owns the deterministic agent runtimes, evidence references, refusal outcomes,
proposal lifecycle records, workflow timings, measurement guards, and
provider-neutral activation attempts shown there. Approval, activation,
deployment, agent invocation, and policy mutation remain server-side.

The console's timings are observed local durations only. They are not evidence
of a commercial delivery-time improvement. The evaluation dataset and harness
test grounding, refusal boundaries, sample protection, observational wording,
and read-only tool allowlists; they do not prove production model safety.

The showcase deliberately leaves authentication on governance endpoints,
authenticated actor verification, a live LLM runtime behind `AgentRuntime`, and
real provider adapters to an implementation engagement. The Analytics Agent
currently refuses before persisting insufficient-sample analyses, so the
console's unmet-guard rendering is defensive rather than a product-reachable
seed scenario.
