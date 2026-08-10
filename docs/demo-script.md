# Presentable demo script: governed workforce loop

This 10–15 minute walkthrough follows:

```text
objective → insight → proposal → human approval → activated draft
→ measured exposures → analysis → recommendation
```

The local runtimes are deterministic. Workflow timings are measured local
durations, not evidence for the commercial delivery-time target.

## Before the room

```bash
MAVEN_MIRROR_URL=https://repo.huaweicloud.com/repository/maven/ \
  docker compose up --build -d
./scripts/seed-demo.sh --reset
```

Wait for `http://localhost:8080/actuator/health` to report `{"status":"UP"}`,
then open `http://localhost:3000/console/workforce`.

The console reconciles a browser-persisted demo login with a reset backend: if
the customer identity remains in local storage but the reset removed its
identity-link row, it says that the identity is present but no link was
recorded for this reset. For a clean identity walkthrough, clear the browser
storage before signing in again.

The reset creates:

- `demo-workforce-miami` — **Family traveler signal effect**, the complete
  governed loop with 100 synthetic exposures.
- `demo-workforce-refusal` — **Explore an unsupported loyalty question**, whose
  Insights Agent produces a real `NO_RELEVANT_SIGNAL` refusal. No unsupported
  insight or analysis is fabricated.

Runtime-generated insight, proposal, execution, analysis, and activation IDs
are intentionally not hard-coded. The objective IDs and business values above
are stable reset values.

## Walkthrough

### 1. Objectives and refusal

1. Confirm the page announces **Loading workforce data** while the first API
   request is pending, if the response is slow.
2. Confirm both objective cards are present.
3. Select **Family traveler signal effect**. Point out lifecycle `ACTIVE`,
   target KPI `BOOKING_COMPLETED`, target `0.20`, and audience
   **family travelers**.
4. Select **Explore an unsupported loyalty question** and expand its execution.
   Show **REFUSED**, code `NO_RELEVANT_SIGNAL`, and the reason that no
   registered signal matched. A refusal is a governed outcome, not an empty
   result.

### 2. Insight and proposal

1. Return to the family objective and follow **Objective → Insight → Proposal**.
2. Expand the insight to show its finding, observed metrics, and reachable
   evidence references. The seeded comparison keeps its qualitative direction
   but withholds comparative rates because at least one evidence group is below
   the platform minimum of 30 sessions.
3. Expand the Experimentation Agent execution to show its tool calls and
   evidence references, then the generated two-arm proposal.

### 3. Human approval gate

Expand **Governance** and show:

- `PROPOSED → APPROVED`;
- actor `demo-marketer`;
- reason **Approve the grounded proposal for the client walkthrough.**;
- `SELF_DECLARED_UNVERIFIED`.

Say aloud that the local actor is attribution only, not proof of an
authenticated approval. Production requires SSO/RBAC.

### 4. Activation and measurement

1. Expand **Activation**. Explain that approval activation creates a
   non-serving `DRAFT` experiment definition; the seed then uses the deliberate
   deployment path for measurement.
   Approval must be completed before clicking **Deploy**. Deploying first is
   rejected by the governance gate with a visible `409`; that is an enforced
   legality check, not a disabled demo button.
   The page re-renders after each transition and the buttons can move, so
   re-locate **Approve**, **Deploy**, or **Rollback** before each click rather
   than clicking the same screen position twice; a stale click silently does
   nothing.
2. Show the provider-neutral `AUDIENCE` and `CAMPAIGN` attempts: destination,
   accepted status, counts, idempotency key, and opaque metadata.
   The collapsed **Provider activation attempts (107)** list is dominated by
   seeded offer-delivery activations; those rows are the expected delivery
   activity behind the count.
3. Point out the 100 total synthetic exposures. On every reset, the
   deterministic experiment assignment produces **52 control** and **48
   personalized** exposures. Do not describe them as commercial traffic.

### 5. Analysis and recommendation

1. Expand **Analysis** and show per-arm exposures and outcomes: control has
   **52 exposures / 5 outcomes (9.6%)** and personalized has **48 exposures /
   7 outcomes (14.6%)**.
2. Confirm `GUARD MET`, **5.0 percentage points absolute lift** and
   **51.7% relative lift**, and the Analytics Agent
   recommendation **ITERATE**. The seeded rates are intentionally plausible
   and modest; this sample was not tuned to manufacture a winner. The
   recommendation reflects that the observed difference did not meet the
   significance threshold.
3. Expand the Analytics execution to show
   `getExperimentPerformance`, `getExperimentExposures`, and
   `getExperimentOutcomes`, with their evidence references.
4. Point out the **Recommendation** stage in the causal strip.
5. Open timings. They are measured durations in this local environment, not a
   causal analysis and not proof of the commercial 50% target.

## Honest boundaries

- Simulated providers prove adapter payload, idempotency, and failure
  representation only; they do not prove provider authentication, rate limits,
  asynchronous behavior, residency, or contract stability.
- Governance actors are self-declared and unverified.
- The console is read-only: it cannot approve, activate, deploy, invoke an
  agent, or change policy.
- The Analytics Agent currently refuses before persisting an
  insufficient-sample analysis. The seeded refusal is the honest
  product-reachable example; it is not presented as a guard-not-met analysis.
- `AgentRuntime` is a future LLM seam; this showcase has deterministic
  runtimes and requires the same evidence/refusal evaluation bar for a future
  LLM implementation.
