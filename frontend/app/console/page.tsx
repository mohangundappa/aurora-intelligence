"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { SiteHeader } from "../../components/SiteHeader";
import { sessionId } from "../../lib/tracker";

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

const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function Console() {
  const [selectedSession, setSelectedSession] = useState("");
  const [data, setData] = useState<ConsoleData | null>(null);
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    const querySession = new URLSearchParams(window.location.search).get(
      "session",
    );
    const current = querySession || sessionId();
    setSelectedSession(current);
    void load(current);
    void loadSessions();
  }, []);

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
