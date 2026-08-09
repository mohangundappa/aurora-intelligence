"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { SiteHeader } from "../../../components/SiteHeader";
import { getApi } from "../../../lib/api";

export default function FunnelPage() {
  const [funnel, setFunnel] = useState<{
    sessionId: string | null;
    stages: { stage: string; sessions: number; dropOff: number }[];
  } | null>(null);
  useEffect(() => {
    const selected =
      new URLSearchParams(window.location.search).get("session") ??
      window.sessionStorage.getItem("aurora.session");
    if (selected) {
      void getApi<typeof funnel>(`/api/console/funnel/${selected}`).then(
        setFunnel,
      );
    } else {
      void getApi<typeof funnel>("/api/console/funnel").then(setFunnel);
    }
  }, []);
  return (
    <main className="console">
      <div className="shell console-shell">
        <SiteHeader surface="console" />
        <nav className="console-nav" aria-label="Console views">
          <Link href="/console">Journey</Link>
          <Link href="/console/lifecycle">Lifecycle</Link>
          <Link href="/console/experiments">Experiments</Link>
          <Link href="/console/funnel">Funnel</Link>
          <Link href="/console/ops">Operations</Link>
        </nav>
        <header className="console-header">
          <div>
            <div className="eyebrow">Journey measurement</div>
            <h1>See where intent moves forward or falls away.</h1>
            <p className="muted">
              Counts are distinct sessions across persisted events; add a
              session filter for the presenter walkthrough.
            </p>
          </div>
        </header>
        <section className="console-card console-wide">
          <span className="pill">CONVERSION FUNNEL</span>
          {funnel?.stages.map((stage) => (
            <div className="event-row" key={stage.stage}>
              <strong>{stage.stage}</strong>
              <span className="muted">
                {stage.sessions} sessions · drop-off {stage.dropOff}
              </span>
            </div>
          ))}
          {(!funnel || funnel.stages.length === 0) && (
            <p className="muted">
              No events have been recorded for this journey yet.
            </p>
          )}
        </section>
      </div>
    </main>
  );
}
