"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { SiteHeader } from "../../../components/SiteHeader";
import { getApi } from "../../../lib/api";

type Performance = {
  experimentId: string;
  name: string;
  description: string;
  primaryOutcomeEvent: string;
  minimumExposuresPerVariant: number;
  variants: {
    name: string;
    exposed: number;
    clicks: number;
    bookingStarts: number;
    completions: number;
    conversionRate: number;
  }[];
  insufficientSample: boolean;
  warning: string;
};

type ExperimentDefinition = {
  id: string;
  name: string;
  description: string;
  lifecycleStatus: string;
};

export default function ExperimentsPage() {
  const [definitions, setDefinitions] = useState<ExperimentDefinition[]>([]);
  const [selectedExperiment, setSelectedExperiment] = useState("");
  const [performance, setPerformance] = useState<Performance | null>(null);

  useEffect(() => {
    void getApi<ExperimentDefinition[]>("/api/experiments").then((loaded) => {
      setDefinitions(loaded);
      setSelectedExperiment(loaded[0]?.id ?? "");
    });
  }, []);

  useEffect(() => {
    if (!selectedExperiment) return;
    void getApi<Performance>(
      `/api/experiments/${selectedExperiment}/performance`,
    ).then(setPerformance);
  }, [selectedExperiment]);

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
            <h2>{performance.name}</h2>
            <p className="muted">{performance.description}</p>
            {definitions.length > 1 && (
              <label>
                Experiment
                <select
                  value={selectedExperiment}
                  onChange={(event) =>
                    setSelectedExperiment(event.target.value)
                  }
                >
                  {definitions.map((definition) => (
                    <option key={definition.id} value={definition.id}>
                      {definition.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
            {performance.insufficientSample && (
              <p className="console-error" role="status">
                Insufficient sample: {performance.warning}
              </p>
            )}
            {performance.variants.map((variant) => (
              <div className="event-row" key={variant.name}>
                <strong>{variant.name}</strong>
                <span>
                  {variant.exposed} exposures · {variant.clicks} clicks ·{" "}
                  {variant.bookingStarts} booking starts · {variant.completions}{" "}
                  {performance.primaryOutcomeEvent.toLowerCase()}
                </span>
                <span className="pill">
                  {performance.insufficientSample
                    ? "Conversion withheld"
                    : `${Math.round(variant.conversionRate * 100)}%`}
                </span>
              </div>
            ))}
          </section>
        )}
      </div>
    </main>
  );
}
