"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { SiteHeader } from "../../../components/SiteHeader";
import { getApi } from "../../../lib/api";

type Insight = {
  insightId: string;
  subject: string;
  finding: string;
  metrics: Record<string, unknown>;
  evidenceRefs: string[];
  createdAt: string;
};

type Analysis = {
  analysisId: string;
  variants: {
    variant: string;
    exposures: number;
    outcomes: number;
    conversionRate: number;
  }[];
  sufficientSample: boolean;
  absoluteLift: number | null;
  relativeLift: number | null;
  recommendation: string;
  reasoning: string;
  evidenceRefs: string[];
};

type ProposalView = {
  proposal: {
    proposalId: string;
    experimentName: string;
    experimentId: string;
    reasoning: string;
    evidenceRefs: string[];
    governanceState: string;
  };
  audit: {
    actor: string;
    actorVerificationStatus: string;
    fromState: string;
    toState: string;
    reason: string;
    createdAt: string;
  }[];
  activationAttempts: {
    operation: string;
    destinationId: string;
    status: string;
    acceptedCount: number;
    rejectedCount: number;
    reason: string | null;
    providerMetadata: Record<string, string>;
    attemptedAt: string;
  }[];
  analyses: Analysis[];
};

type Execution = {
  executionId: string;
  agentType: string;
  status: string;
  startedAt: string;
  completedAt: string;
  latencyMilliseconds: number;
  output: unknown;
  toolCalls: {
    toolName: string;
    resultReference: string;
    status: string;
    result: unknown;
  }[];
  errors: string[];
};

type ObjectiveView = {
  objective: {
    objectiveId: string;
    name: string;
    description: string;
    status: string;
    targetKpi: string;
    targetValue: number;
  };
  insights: Insight[];
  proposals: ProposalView[];
  executions: Execution[];
  timings: {
    stage: string;
    elapsedMilliseconds: number;
    startedAt: string;
    completedAt: string;
  }[];
};

type WorkforceView = {
  objectives: ObjectiveView[];
  executions: Execution[];
  activationAttempts: {
    operation: string;
    destinationId: string;
    status: string;
    acceptedCount: number;
    rejectedCount: number;
    reason: string | null;
    providerMetadata: Record<string, string>;
    attemptedAt: string;
    contextId: string | null;
  }[];
};

function EmptyState({ children }: { children: string }) {
  return <p className="empty-state">{children}</p>;
}

function Evidence({ refs }: { refs: string[] }) {
  return (
    <details className="evidence">
      <summary>Evidence cited ({refs.length})</summary>
      {refs.length ? (
        <ul>
          {refs.map((reference) => (
            <li key={reference}>
              <code>{reference}</code>
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState>No evidence references were recorded.</EmptyState>
      )}
    </details>
  );
}

function refusalFromOutput(output: unknown) {
  if (
    typeof output === "object" &&
    output !== null &&
    "code" in output &&
    "reason" in output &&
    typeof output.code === "string" &&
    typeof output.reason === "string"
  ) {
    return { code: output.code, reason: output.reason };
  }
  return null;
}

function ExecutionCard({ execution }: { execution: Execution }) {
  const refusal = refusalFromOutput(execution.output);
  const refused =
    execution.status.toUpperCase().includes("REFUS") ||
    execution.errors.length > 0 ||
    refusal !== null;
  return (
    <article className={`execution-card ${refused ? "refusal" : ""}`}>
      <div className="event-row">
        <strong>{execution.agentType}</strong>
        <span className="pill">{execution.status}</span>
      </div>
      {refused && (
        <p className="refusal-copy">
          {refusal
            ? `Agent refusal: ${refusal.code} · ${refusal.reason}`
            : `Refusal or failed obligation: ${
                execution.errors.join(" · ") ||
                "See the recorded output for the refusal code and reason."
              }`}
        </p>
      )}
      <p className="muted">
        {execution.latencyMilliseconds} ms measured runtime duration ·{" "}
        {new Date(execution.startedAt).toLocaleString()}
      </p>
      <details>
        <summary>Agent output and tool evidence</summary>
        <pre className="json-block">
          {JSON.stringify(execution.output, null, 2)}
        </pre>
        {execution.toolCalls.length ? (
          execution.toolCalls.map((call) => (
            <details className="tool-call" key={call.resultReference}>
              <summary>
                {call.toolName} · {call.status}
              </summary>
              <p className="muted">
                Evidence reference: {call.resultReference}
              </p>
              <pre className="json-block">
                {JSON.stringify(call.result, null, 2)}
              </pre>
            </details>
          ))
        ) : (
          <EmptyState>No tool calls were recorded.</EmptyState>
        )}
      </details>
    </article>
  );
}

export default function WorkforcePage() {
  const [view, setView] = useState<WorkforceView | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void getApi<WorkforceView>("/api/console/workforce")
      .then(setView)
      .catch((reason: unknown) => {
        setError(
          reason instanceof Error
            ? reason.message
            : "Unable to load workforce data.",
        );
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <main className="console">
      <div className="shell console-shell">
        <SiteHeader surface="console" />
        <nav className="console-nav" aria-label="Console views">
          <Link href="/console">Journey</Link>
          <Link href="/console/workforce">Workforce loop</Link>
          <Link href="/console/lifecycle">Lifecycle</Link>
          <Link href="/console/experiments">Experiments</Link>
          <Link href="/console/funnel">Funnel</Link>
          <Link href="/console/ops">Operations</Link>
        </nav>
        <header className="console-header">
          <div>
            <div className="eyebrow">Digital workforce</div>
            <h1>Follow the governed loop from objective to evidence.</h1>
            <p className="muted">
              Read-only view of what agents proposed, what humans governed, and
              what the measured data supports.
            </p>
          </div>
        </header>
        {error && (
          <p className="console-error" role="alert">
            {error}
          </p>
        )}
        {!error && loading && (
          <section className="console-card console-wide" role="status">
            <h2>Loading workforce data</h2>
            <p className="muted">
              Reading the governed objective-to-evidence loop…
            </p>
          </section>
        )}
        {!error && view && view.objectives.length === 0 && (
          <section className="console-card console-wide">
            <h2>No objectives yet</h2>
            <EmptyState>
              Create a business objective before the workforce loop can be
              shown.
            </EmptyState>
          </section>
        )}
        {view && view.activationAttempts.length > 0 && (
          <section className="console-card console-wide workforce-section">
            <h2>Provider activation attempts</h2>
            <p className="muted">
              Durable hand-off evidence, including offer delivery attempts that
              are not tied to an experiment proposal.
            </p>
            {view.activationAttempts.map((attempt) => (
              <div
                className="audit-row"
                key={`${attempt.attemptedAt}-${attempt.destinationId}`}
              >
                <strong>
                  {attempt.operation} · {attempt.destinationId}
                </strong>
                <span className="pill">{attempt.status}</span>
                <small>
                  {attempt.acceptedCount} accepted · {attempt.rejectedCount}{" "}
                  rejected
                  {attempt.reason ? ` · ${attempt.reason}` : ""}
                </small>
                <code>{JSON.stringify(attempt.providerMetadata)}</code>
              </div>
            ))}
          </section>
        )}
        {view?.objectives.map((item) => (
          <section
            className="console-card console-wide workforce-objective"
            key={item.objective.objectiveId}
          >
            <div className="workforce-objective-heading">
              <div>
                <span className="pill">
                  OBJECTIVE · {item.objective.status}
                </span>
                <h2>{item.objective.name}</h2>
                <p>{item.objective.description}</p>
                <p className="muted">
                  Target: {item.objective.targetKpi} ·{" "}
                  {item.objective.targetValue}
                </p>
              </div>
              <code>{item.objective.objectiveId}</code>
            </div>
            <div className="workforce-stage">
              <span>Objective</span>
              <span>Insight</span>
              <span>Proposal</span>
              <span>Governance</span>
              <span>Analysis</span>
            </div>

            <section className="workforce-section">
              <h3>1. Grounded insights</h3>
              {item.insights.length ? (
                item.insights.map((insight) => (
                  <article className="detail-card" key={insight.insightId}>
                    <strong>{insight.subject}</strong>
                    <p>{insight.finding}</p>
                    <Evidence refs={insight.evidenceRefs} />
                  </article>
                ))
              ) : (
                <EmptyState>
                  No insights have been generated for this objective yet.
                </EmptyState>
              )}
            </section>

            <section className="workforce-section">
              <h3>2. Proposals and governance</h3>
              {item.proposals.length ? (
                item.proposals.map((proposal) => (
                  <article
                    className="detail-card"
                    key={proposal.proposal.proposalId}
                  >
                    <div className="event-row">
                      <strong>{proposal.proposal.experimentName}</strong>
                      <span className="pill">
                        {proposal.proposal.governanceState}
                      </span>
                    </div>
                    <p>{proposal.proposal.reasoning}</p>
                    <Evidence refs={proposal.proposal.evidenceRefs} />
                    <h4>Governance audit</h4>
                    {proposal.audit.length ? (
                      proposal.audit.map((audit) => (
                        <div
                          className="audit-row"
                          key={`${audit.createdAt}-${audit.toState}`}
                        >
                          <strong>
                            {audit.fromState} → {audit.toState}
                          </strong>
                          <span>
                            {audit.actor} · {audit.actorVerificationStatus} ·{" "}
                            {new Date(audit.createdAt).toLocaleString()}
                          </span>
                          <small>{audit.reason}</small>
                        </div>
                      ))
                    ) : (
                      <EmptyState>
                        No governance transition has been recorded.
                      </EmptyState>
                    )}
                    <h4>Activation attempts</h4>
                    {proposal.activationAttempts.length ? (
                      proposal.activationAttempts.map((attempt) => (
                        <div className="audit-row" key={attempt.attemptedAt}>
                          <strong>
                            {attempt.operation} · {attempt.destinationId}
                          </strong>
                          <span className="pill">{attempt.status}</span>
                          <small>
                            {attempt.acceptedCount} accepted ·{" "}
                            {attempt.rejectedCount} rejected
                            {attempt.reason ? ` · ${attempt.reason}` : ""}
                          </small>
                        </div>
                      ))
                    ) : (
                      <EmptyState>
                        No provider activation attempt has been recorded.
                      </EmptyState>
                    )}
                    <h4>Analyses</h4>
                    {proposal.analyses.length ? (
                      proposal.analyses.map((analysis) => (
                        <div
                          className="analysis-card"
                          key={analysis.analysisId}
                        >
                          <div className="event-row">
                            <strong>{analysis.recommendation}</strong>
                            <span className="pill">
                              {analysis.sufficientSample
                                ? "GUARD MET"
                                : "GUARD NOT MET"}
                            </span>
                          </div>
                          <div className="variant-list">
                            {analysis.variants.map((variant) => (
                              <span key={variant.variant}>
                                {variant.variant}: {variant.exposures} exposures
                                · {variant.outcomes} outcomes
                                {analysis.sufficientSample
                                  ? ` · ${(variant.conversionRate * 100).toFixed(1)}%`
                                  : ""}
                              </span>
                            ))}
                          </div>
                          {analysis.sufficientSample ? (
                            <p>{analysis.reasoning}</p>
                          ) : (
                            <p className="console-error" role="status">
                              Evidence guard not met. Lift and conclusion are
                              withheld.
                            </p>
                          )}
                          <Evidence refs={analysis.evidenceRefs} />
                        </div>
                      ))
                    ) : (
                      <EmptyState>
                        No analysis has been recorded for this experiment yet.
                      </EmptyState>
                    )}
                  </article>
                ))
              ) : (
                <EmptyState>
                  No experiment proposal has been generated yet.
                </EmptyState>
              )}
            </section>

            <section className="workforce-section">
              <h3>3. Agent executions and evidence</h3>
              {item.executions.length ? (
                item.executions.map((execution) => (
                  <ExecutionCard
                    execution={execution}
                    key={execution.executionId}
                  />
                ))
              ) : (
                <EmptyState>
                  No agent execution has been recorded for this objective yet.
                </EmptyState>
              )}
            </section>

            <section className="workforce-section">
              <h3>4. Workflow timings</h3>
              <p className="muted">
                Durations measured in this environment; they are not a claim of
                causal improvement or production performance.
              </p>
              {item.timings.length ? (
                item.timings.map((timing) => (
                  <div
                    className="event-row"
                    key={`${timing.stage}-${timing.completedAt}`}
                  >
                    <strong>{timing.stage}</strong>
                    <span>
                      {timing.elapsedMilliseconds} ms measured duration
                    </span>
                  </div>
                ))
              ) : (
                <EmptyState>
                  No workflow timing has been recorded yet.
                </EmptyState>
              )}
            </section>
          </section>
        ))}
      </div>
    </main>
  );
}
