"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { SiteHeader } from "../../../components/SiteHeader";
import { getApi } from "../../../lib/api";

type Ops = {
  dataQuality: {
    ingestCount: number;
    quarantineCount: number;
    quarantineRate: number;
    quarantineReasons: Record<string, number>;
    decisionLatencyMs: number;
    consumerLagMs: number;
    signalFreshnessDistribution: { signal: string; freshnessMinutes: number }[];
  };
  components: Record<string, string>;
};

export default function OpsPage() {
  const [ops, setOps] = useState<Ops | null>(null);
  useEffect(() => {
    void getApi<Ops>("/api/console/ops").then(setOps);
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
            <div className="eyebrow">Operational confidence</div>
            <h1>Know whether the intelligence path is healthy.</h1>
            <p className="muted">
              Quality metrics are evidence about the system, not marketing
              claims.
            </p>
          </div>
        </header>
        {ops && (
          <div className="console-grid">
            <section className="console-card">
              <span className="pill">DATA QUALITY</span>
              <h2>{ops.dataQuality.ingestCount} ingested events</h2>
              <p className="muted">
                {ops.dataQuality.quarantineCount} quarantined ·{" "}
                {Math.round(ops.dataQuality.quarantineRate * 100)}% quarantine
                rate
              </p>
              {Object.entries(ops.dataQuality.quarantineReasons).map(
                ([reason, count]) => (
                  <div className="event-row" key={reason}>
                    <span>{reason}</span>
                    <strong>{count}</strong>
                  </div>
                ),
              )}
            </section>
            <section className="console-card">
              <span className="pill">PIPELINE HEALTH</span>
              <h2>{Math.round(ops.dataQuality.decisionLatencyMs)} ms</h2>
              <p className="muted">Average decision latency</p>
              <p className="muted">
                Consumer lag equivalent:{" "}
                {Math.round(ops.dataQuality.consumerLagMs)} ms
              </p>
              {Object.entries(ops.components).map(([component, status]) => (
                <div className="event-row" key={component}>
                  <span>{component}</span>
                  <span className="pill">{status}</span>
                </div>
              ))}
            </section>
            <section className="console-card console-wide">
              <span className="pill">SIGNAL FRESHNESS</span>
              {ops.dataQuality.signalFreshnessDistribution.map((item) => (
                <div className="event-row" key={item.signal}>
                  <span>{item.signal}</span>
                  <span>{Math.round(item.freshnessMinutes)} minutes</span>
                </div>
              ))}
            </section>
          </div>
        )}
      </div>
    </main>
  );
}
