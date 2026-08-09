"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { SiteHeader } from "../../../components/SiteHeader";
import { getApi, postApi } from "../../../lib/api";

type Model = { version: string; status: string };
type Signal = { signalName: string; version: string; status: string };

export default function LifecyclePage() {
  const [models, setModels] = useState<Model[]>([]);
  const [signals, setSignals] = useState<Signal[]>([]);

  async function refresh() {
    setModels(await getApi<Model[]>("/api/models/booking-intent"));
    setSignals(await getApi<Signal[]>("/api/signals/lifecycle"));
  }

  useEffect(() => {
    void refresh();
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
            <div className="eyebrow">Rollout control</div>
            <h1>Move intelligence from draft to deployed.</h1>
            <p className="muted">
              Every transition is explicit and recorded for presenter review.
            </p>
          </div>
        </header>
        <div className="console-grid">
          <section className="console-card console-wide">
            <span className="pill">MODEL LIFECYCLE</span>
            <h2>Booking-intent</h2>
            {models.map((model) => (
              <div className="event-row" key={model.version}>
                <strong>Version {model.version}</strong>
                <span className="pill">{model.status}</span>
                {model.status === "TESTED" && (
                  <button
                    type="button"
                    onClick={async () => {
                      await postApi(
                        `/api/models/booking-intent/${model.version}/approve`,
                      );
                      await refresh();
                    }}
                  >
                    Approve
                  </button>
                )}
                {model.status !== "DEPLOYED" && (
                  <button
                    type="button"
                    onClick={async () => {
                      await postApi(
                        `/api/models/booking-intent/${model.version}/deploy`,
                      );
                      await refresh();
                    }}
                  >
                    Deploy / rollback
                  </button>
                )}
              </div>
            ))}
          </section>
          <section className="console-card console-wide">
            <span className="pill">SIGNAL LIFECYCLE</span>
            <h2>Definition rollout</h2>
            {signals.map((signal) => (
              <div className="event-row" key={signal.signalName}>
                <strong>{signal.signalName}</strong>
                <span className="pill">{signal.status}</span>
                <button
                  type="button"
                  onClick={async () => {
                    await postApi(
                      `/api/signals/lifecycle/${signal.signalName}/TESTED`,
                    );
                    await refresh();
                  }}
                >
                  Test
                </button>
                <button
                  type="button"
                  onClick={async () => {
                    await postApi(
                      `/api/signals/lifecycle/${signal.signalName}/APPROVED`,
                    );
                    await refresh();
                  }}
                >
                  Approve
                </button>
                <button
                  type="button"
                  onClick={async () => {
                    await postApi(
                      `/api/signals/lifecycle/${signal.signalName}/DEPLOYED`,
                    );
                    await refresh();
                  }}
                >
                  Deploy
                </button>
              </div>
            ))}
          </section>
        </div>
      </div>
    </main>
  );
}
