"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { SiteHeader } from "../../../components/SiteHeader";
import { getApi } from "../../../lib/api";

export default function FunnelPage() {
  const [funnel, setFunnel] = useState<Record<string, number>>({});
  useEffect(() => {
    const selected =
      new URLSearchParams(window.location.search).get("session") ??
      window.sessionStorage.getItem("aurora.session");
    if (selected) {
      void getApi<Record<string, number>>(
        `/api/console/funnel/${selected}`,
      ).then(setFunnel);
    }
  }, []);
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
            <div className="eyebrow">Journey measurement</div>
            <h1>See where intent moves forward or falls away.</h1>
            <p className="muted">
              Counts are calculated from persisted events for the selected
              browser journey.
            </p>
          </div>
        </header>
        <section className="console-card console-wide">
          <span className="pill">CONVERSION FUNNEL</span>
          {Object.entries(funnel).map(([event, count]) => (
            <div className="event-row" key={event}>
              <strong>{event}</strong>
              <span className="signal-score">{count}</span>
            </div>
          ))}
          {Object.keys(funnel).length === 0 && (
            <p className="muted">
              No events have been recorded for this journey yet.
            </p>
          )}
        </section>
      </div>
    </main>
  );
}
