"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { SiteHeader } from "../../../components/SiteHeader";
import { getApi } from "../../../lib/api";

type Performance = {
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

export default function ExperimentsPage() {
  const [performance, setPerformance] = useState<Performance | null>(null);
  useEffect(() => {
    void getApi<Performance>(
      "/api/experiments/destination-experience-v1/performance",
    ).then(setPerformance);
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
            <div className="eyebrow">Measurement guardrails</div>
            <h1>Know when the evidence is ready.</h1>
            <p className="muted">
              Exposures and outcomes are joined through the decision correlation
              ID.
            </p>
          </div>
        </header>
        {performance && (
          <section className="console-card console-wide">
            <span className="pill">EXPERIMENT PERFORMANCE</span>
            <h2>Destination experience test</h2>
            {performance.insufficientSample && (
              <p className="console-error" role="status">
                Insufficient sample: {performance.warning}
              </p>
            )}
            {[
              ["Control", performance.control],
              ["Treatment", performance.treatment],
            ].map(([name, variant]) => (
              <div className="event-row" key={name as string}>
                <strong>{name as string}</strong>
                <span>
                  {(variant as Performance["control"]).exposed} exposures ·{" "}
                  {(variant as Performance["control"]).clicks} clicks ·{" "}
                  {(variant as Performance["control"]).bookingStarts} booking
                  starts · {(variant as Performance["control"]).completions}{" "}
                  completions
                </span>
                <span className="pill">
                  {performance.insufficientSample
                    ? "Conversion withheld"
                    : `${Math.round((variant as Performance["control"]).conversionRate * 100)}%`}
                </span>
              </div>
            ))}
          </section>
        )}
      </div>
    </main>
  );
}
