# Value thesis: from idea to measured launch

Aurora's value story is a before/after timeline, not a promise that an
enterprise program becomes instantaneous. The MVP demonstrates where reusable
artifacts and governed configuration remove repeated engineering work. It
does not make data access, security review, vendor integration, or production
operations disappear.

## The traditional path: approximately three months

| Stage | What consumes time in a real enterprise | Evidence in this repository | Limitation |
|---|---|---|---|
| Idea | Define the audience, use case, KPI, and decision | `docs/demo-script.md` and `decision/src/main/resources/decision-policy.yaml` show a concrete recommendation and policy rule. | This is a worked showcase, not a measure of stakeholder alignment or campaign approval time. |
| Find data | Discover sources, obtain access, negotiate schemas, and establish data quality | `common/src/main/java/com/aurora/common/EventCatalog.java`, `docs/event-catalog.md`, and `POST /api/v1/events` show the governed canonical envelope and validation boundary. | The demo seed supplies known data. It does not measure enterprise discovery, access approval, or source remediation. |
| Build signals/model | Implement calculations, evaluate them, and establish lifecycle controls | `signals/src/main/resources/signals/`, `SignalRegistry`, calculator beans such as `WeekendGetawayAffinityCalculator`, and `models/` show the reusable framework. | The calculators and baseline model are small MVP implementations; production validation still requires representative data and governance. |
| Integration | Connect identity, profiles, audiences, activation, and consent semantics | `cdp/src/main/java/com/aurora/cdp/CdpAdapter.java` and `SimulatedCdpAdapter` show the provider-neutral seam. | The simulator bypasses real vendor integration. It does not evidence a completed Adobe, Salesforce, Tealium, or Segment implementation. |
| Experiment | Define arms, allocation, outcome, and guardrails | `experiments/src/main/resources/experiments/destination-experience-v1.yaml`, `ExperimentRegistry`, and `GET /api/experiments` show configuration-driven experiment setup. | The committed definition is one local showcase experiment; enterprise experimentation still needs platform integration, approvals, and operating procedures. |
| Measurement | Join exposure to outcome and decide whether evidence is ready | `experiment_exposures`, `experiment_outcomes`, `ExperimentService`, `/console/experiments`, and the correlation ID carried by event envelopes show the measurement path. | Seeded traffic is synthetic. The sample guard prevents premature claims; it is not statistical proof of commercial lift. |

The first and last stages are visible in the demo. The data and integration
stages are where a real enterprise program still spends substantial time.

## The accelerated path demonstrated by Aurora

| Stage | What Aurora demonstrates | Skeptic-facing artifact | Honest boundary |
|---|---|---|---|
| Idea | A business rule can be expressed as a decision policy over explainable signals. | `decision/src/main/resources/decision-policy.yaml`; `GET /api/sessions/{sessionId}/decision`. | The policy is configured for the showcase and still needs business approval in production. |
| Reuse signals/model | A new signal is a YAML definition plus a calculator bean discovered by a registry, without a central code edit. | `signals/src/main/resources/signals/weekend-getaway-affinity.yaml`, `signals/src/main/java/com/aurora/signals/WeekendGetawayAffinityCalculator.java`, and `SignalRegistry`. Model reuse is visible through `ModelService` and its version lifecycle APIs. | Reuse shortens implementation work; it does not remove data validation or model-risk review. |
| Configure decision | Decision rules, thresholds, reason codes, and experience are configuration-driven. | `decision-policy.yaml`, `DecisionPolicy`, and the decision endpoint. | This MVP has one policy file and does not provide a full marketer authoring UI. |
| Configure experiment | Experiment metadata, declared variants, allocation, primary outcome, threshold, and lifecycle are YAML-defined and discovered at startup. | `experiments/src/main/resources/experiments/destination-experience-v1.yaml`, `ExperimentRegistry`, `GET /api/experiments`, and `/console/experiments`. | The registry proves configuration-driven setup; production still needs approval, change control, and provider-specific activation. |
| Launch | Lifecycle controls govern signal/model rollout, deployment, rollback, and audit. | `SignalLifecycleController`, `ModelController`, `/console/lifecycle`, and the lifecycle audit tables. | The console is a showcase control surface, not a complete enterprise release-management system. |
| Measure | Stable assignment, exposure persistence, outcome persistence, correlation-ID joins, conversion rates, and sample guardrails are implemented. | `ExperimentService`, `experiment_exposures`, `experiment_outcomes`, `GET /api/experiments/{experimentId}/performance`, and `/console/experiments`. | `Measurement` computes absolute and relative lift for its two-arm helper, while the current performance view emphasizes per-variant counts/rates and withholds claims below the configured threshold. Seed volume is synthetic, not commercial evidence. |

## Find data and integration: reduced, not eliminated

The MVP intentionally makes the data and integration stages look easy by
supplying a simulator behind `CdpAdapter`. Identity, profile, and audience-like
context therefore arrive without a client access project. That is useful for
showing the signal-to-decision mechanics, but it is not a claim that the
three-month enterprise work vanishes.

The defensible claim is narrower:

- `EventCatalog` gives the implementation team a governed event contract and
  explicit quarantine behavior.
- `CdpAdapter` gives the integration team a provider-neutral seam.
- identity stitching, consent, profile hydration, and decision correlation have
  explicit interfaces and persisted facts that can be mapped to a real CDP.

In production, “find data” still includes discovery, access approval, schema
negotiation, data quality remediation, retention decisions, and consent
mapping. “Integration” still includes a real Adobe, Salesforce, Tealium, or
Segment connection, identity semantics, activation destinations, security
review, and operational ownership.

## What the 50% figure means

The 50% figure is a target derived from the documented assumptions in
`app/src/main/resources/delivery-assumptions.yaml`. That file currently covers
only three activities:

1. signal definition and validation;
2. offline model evaluation;
3. deployment and rollback.

It compares the `traditionalDays` and `acceleratedDays` values for those
activities through `DeliveryComparisonService` and the delivery view. It does
not measure the whole idea-to-measurement timeline, does not include the
enterprise data and integration work described above, and is not a proven
commercial outcome. The credible use of the number is as a planning target
whose assumptions a client can challenge and replace with its own baseline.

## Workforce proof point

The seeded `/console/workforce` walkthrough provides a concrete artifact for
the idea-to-measurement portion of the story: an objective produces a grounded
insight, an agent proposes an experiment, a human approval is audited, Aurora
registers provider-neutral activation attempts, and measurement feeds a guarded
analysis. This reduces repeated implementation work in the showcase; it does
not eliminate data discovery, integration, security review, authentication,
provider implementation, or production operating work.
