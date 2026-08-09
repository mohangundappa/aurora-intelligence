"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { SiteHeader } from "../../components/SiteHeader";
import { sessionId } from "../../lib/tracker";
import { getApi } from "../../lib/api";

type ConsoleData = {
  events: {
    eventId: string;
    eventName: string;
    eventTime: string;
    payload: Record<string, unknown>;
  }[];
  context: {
    profile: {
      identity: {
        anonymousId: string;
        customerId: string | null;
        identified: boolean;
      };
      loyalty: { tier: string; points: number };
      audiences: string[];
    };
    activeSignals: {
      name: string;
      value: number;
      confidence: number;
      explanation: string;
      provenance: string;
      expiresAt: string;
    }[];
    journeyStage: string;
  };
  decision: {
    experience: string;
    reasonCodes: string[];
    explanation: string;
    correlationId: string;
  };
};

type SessionSummary = {
  sessionId: string;
  destination: string | null;
  customerId: string | null;
  anonymousId: string;
  lastActivity: string;
};

type ModelVersion = {
  modelName: string;
  version: string;
  status: string;
  features: string[];
};

type ExperimentPerformance = {
  experimentId: string;
  control: {
    exposed: number;
    clicks: number;
    bookingStarts: number;
    completions: number;
    conversionRate: number;
  };
  treatment: {
    exposed: number;
    clicks: number;
    bookingStarts: number;
    completions: number;
    conversionRate: number;
  };
  insufficientSample: boolean;
  warning: string;
};

type DeliveryComparison = {
  assumptions: {
    activity: string;
    traditionalDays: number;
    acceleratedDays: number;
    rationale: string;
  }[];
  reduction: number;
  label: string;
};

const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function Console() {
  const [selectedSession, setSelectedSession] = useState("");
  const [data, setData] = useState<ConsoleData | null>(null);
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [models, setModels] = useState<ModelVersion[]>([]);
  const [experiment, setExperiment] = useState<ExperimentPerformance | null>(
    null,
  );
  const [delivery, setDelivery] = useState<DeliveryComparison | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const querySession = new URLSearchParams(window.location.search).get(
      "session",
    );
    const current = querySession || sessionId();
    setSelectedSession(current);
    void load(current);
    void loadSessions();
    void loadModels();
    void loadExperiment();
    void loadDelivery();
  }, []);

  async function loadModels() {
    const response = await fetch(`${API}/api/models/booking-intent`);
    if (response.ok) setModels(await response.json());
  }

  async function loadExperiment() {
    const response = await fetch(
      `${API}/api/experiments/destination-experience-v1/performance`,
    );
    if (response.ok) setExperiment(await response.json());
  }

  async function loadDelivery() {
    setDelivery(await getApi<DeliveryComparison>("/api/console/delivery"));
  }

  async function changeModel(
    version: string,
    action: "approve" | "deploy" | "rollback",
  ) {
    const response = await fetch(
      `${API}/api/models/booking-intent/${version}/${action}`,
      { method: "POST" },
    );
    if (response.ok) void loadModels();
  }

  async function loadSessions() {
    const response = await fetch(`${API}/api/console/sessions`);
    if (response.ok) setSessions(await response.json());
  }

  async function load(value: string) {
    if (!value) return;
    setError("");
    const response = await fetch(`${API}/api/console/sessions/${value}`);
    if (!response.ok) {
      setError("No journey is available yet. Search the Aurora site first.");
      return;
    }
    setData(await response.json());
  }

  return (
    <main className="console">
      <div className="shell console-shell">
        <SiteHeader />
        <nav className="console-nav" aria-label="Console views">
          <Link href="/console">Journey</Link>
          <Link href="/console/lifecycle">Lifecycle</Link>
          <Link href="/console/experiments">Experiments</Link>
          <Link href="/console/funnel">Funnel</Link>
          <Link href="/console/ops">Operations</Link>
        </nav>
        <header className="console-header">
          <div>
            <div className="eyebrow">Marketing intelligence console</div>
            <h1>See the why behind every decision.</h1>
            <p className="muted">
              A presenter-friendly view of observed behavior, derived signals,
              and next-best action.
            </p>
          </div>
          <div className="console-session">
            <label htmlFor="session-select">Selected journey</label>
            <select
              id="session-select"
              value={selectedSession}
              onChange={(event) => {
                setSelectedSession(event.target.value);
                void load(event.target.value);
              }}
            >
              <option value={sessionId()}>This browser session</option>
              {sessions.map((session) => (
                <option value={session.sessionId} key={session.sessionId}>
                  {session.customerId || "Anonymous"} ·{" "}
                  {session.destination || "Undirected journey"} ·{" "}
                  {new Date(session.lastActivity).toLocaleTimeString()}
                </option>
              ))}
            </select>
            <Link href="/">Open Aurora site →</Link>
          </div>
        </header>
        {error && (
          <p className="console-error" role="alert">
            {error}
          </p>
        )}
        {data && (
          <div className="console-grid">
            <section className="console-card console-wide">
              <span className="pill">MODEL & SIGNAL LIFECYCLE</span>
              <h2>Booking-intent rollout</h2>
              <p className="muted">
                Register, approve, deploy, or roll back a version while the
                explainable signal remains observable.
              </p>
              {models.map((model) => (
                <div className="event-row" key={model.version}>
                  <strong>Version {model.version}</strong>
                  <span className="pill">{model.status}</span>
                  {(model.status === "TESTED" || model.status === "DRAFT") && (
                    <button
                      type="button"
                      onClick={() => void changeModel(model.version, "approve")}
                    >
                      Approve
                    </button>
                  )}
                  {model.status !== "DEPLOYED" && (
                    <button
                      type="button"
                      onClick={() => void changeModel(model.version, "deploy")}
                    >
                      Deploy / rollback
                    </button>
                  )}
                </div>
              ))}
            </section>
            {experiment && (
              <section className="console-card console-wide">
                <span className="pill">EXPERIMENT PERFORMANCE</span>
                <h2>Destination experience test</h2>
                {experiment.insufficientSample && (
                  <p className="console-error" role="status">
                    Insufficient sample: {experiment.warning}
                  </p>
                )}
                <div className="signal-table">
                  {[experiment.control, experiment.treatment].map((variant) => (
                    <article className="signal-row" key={variant.exposed}>
                      <div>
                        <strong>
                          {variant === experiment.control
                            ? "Control"
                            : "Treatment"}
                        </strong>
                        <p className="muted">
                          {variant.exposed} exposures · {variant.clicks} clicks
                          · {variant.bookingStarts} booking starts
                        </p>
                      </div>
                      <div className="signal-score">
                        <strong>{variant.completions}</strong>
                        <span>booking completions</span>
                        <span>
                          {experiment.insufficientSample
                            ? "Conversion held until sample is sufficient"
                            : `${Math.round(variant.conversionRate * 100)}% conversion`}
                        </span>
                      </div>
                    </article>
                  ))}
                </div>
              </section>
            )}
            {delivery && (
              <section className="console-card console-wide">
                <span className="pill">DELIVERY COMPARISON</span>
                <h2>Reusable delivery target</h2>
                <p className="muted">{delivery.label}</p>
                <div className="signal-table">
                  {delivery.assumptions.map((assumption) => (
                    <article className="signal-row" key={assumption.activity}>
                      <div>
                        <strong>{assumption.activity}</strong>
                        <p className="muted">{assumption.rationale}</p>
                      </div>
                      <div className="signal-score">
                        <span>
                          Traditional: {assumption.traditionalDays} days
                        </span>
                        <span>
                          Accelerated: {assumption.acceleratedDays} days
                        </span>
                      </div>
                    </article>
                  ))}
                </div>
                <p className="muted">
                  Computed target reduction from these assumptions:{" "}
                  {(delivery.reduction * 100).toFixed(1)}%.
                </p>
              </section>
            )}
            <section className="console-card console-wide">
              <span className="pill">PROFILE & IDENTITY</span>
              <h2>
                {data.context.profile.identity.identified
                  ? data.context.profile.identity.customerId
                  : "Anonymous visitor"}
              </h2>
              <p className="muted">
                {data.context.profile.loyalty.tier} ·{" "}
                {data.context.profile.loyalty.points} points · Journey stage:{" "}
                <strong>{data.context.journeyStage}</strong>
              </p>
              <p className="muted">
                Anonymous ID: {data.context.profile.identity.anonymousId}
              </p>
              <div className="timeline">
                <span>Observed</span>
                <span>Signals derived</span>
                <span>Decision explained</span>
              </div>
            </section>
            <section className="console-card">
              <span className="pill">RAW EVENTS</span>
              <h2>What happened</h2>
              {data.events.map((event) => (
                <div className="event-row" key={event.eventId}>
                  <strong>{event.eventName}</strong>
                  <span className="muted">{JSON.stringify(event.payload)}</span>
                </div>
              ))}
            </section>
            <section className="console-card">
              <span className="pill">AUDIENCES</span>
              <h2>Who this resembles</h2>
              {data.context.profile.audiences.length ? (
                data.context.profile.audiences.map((audience) => (
                  <p key={audience}>{audience}</p>
                ))
              ) : (
                <p className="muted">No audience memberships yet.</p>
              )}
              <p className="muted">
                CDP simulator · local stand-in alongside a CDP platform
              </p>
            </section>
            <section className="console-card console-wide">
              <span className="pill">DERIVED SIGNALS</span>
              <h2>Intelligence with provenance</h2>
              <div className="signal-table">
                {data.context.activeSignals.map((signal) => (
                  <article className="signal-row" key={signal.name}>
                    <div>
                      <strong>{signal.name}</strong>
                      <p className="muted">{signal.explanation}</p>
                      <small>{signal.provenance}</small>
                    </div>
                    <div className="signal-score">
                      <strong>{signal.value}</strong>
                      <span>confidence {signal.confidence}</span>
                      <span>
                        fresh until{" "}
                        {new Date(signal.expiresAt).toLocaleTimeString()}
                      </span>
                    </div>
                  </article>
                ))}
              </div>
            </section>
            <section className="console-card console-wide decision-card">
              <span className="pill">NEXT-BEST ACTION</span>
              <h2>{data.decision.experience}</h2>
              <p>{data.decision.explanation}</p>
              <p className="muted">
                Reason codes: {data.decision.reasonCodes.join(" · ")}
              </p>
              <small>Correlation ID: {data.decision.correlationId}</small>
            </section>
          </div>
        )}
      </div>
    </main>
  );
}
