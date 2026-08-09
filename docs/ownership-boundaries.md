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
