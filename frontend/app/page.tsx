"use client";
import { useState } from "react";
const API = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
export default function Home() {
  const [destination, setDestination] = useState("");
  const [result, setResult] = useState("");
  const [session] = useState(() => crypto.randomUUID());
  const [anonymous] = useState(() => crypto.randomUUID());
  async function search() {
    const correlation = crypto.randomUUID();
    const base = {
      eventTime: new Date().toISOString(),
      receivedTime: new Date().toISOString(),
      schemaVersion: "1.0",
      source: "aurora-web",
      sessionId: session,
      anonymousId: anonymous,
      customerId: null,
      correlationId: correlation,
      consent: { analytics: true, personalization: true },
    };
    await fetch(`${API}/api/v1/events`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Correlation-Id": correlation,
      },
      body: JSON.stringify([
        {
          ...base,
          eventId: crypto.randomUUID(),
          eventName: "PAGE_VIEWED",
          payload: { path: "/" },
        },
        {
          ...base,
          eventId: crypto.randomUUID(),
          eventName: "DESTINATION_SEARCHED",
          payload: { destination },
        },
      ]),
    });
    const d = await fetch(`${API}/api/sessions/${session}/decision`).then((r) =>
      r.json(),
    );
    setResult(
      d.experience === "MIAMI_GETAWAY"
        ? `A tailored Miami escape is ready for you — ${d.explanation}`
        : "Explore Aurora Hotels at your own pace.",
    );
  }
  return (
    <main className="shell">
      <nav className="nav">
        <div className="brand">AURORA HOTELS</div>
        <a href="/console">Marketing Console ↗</a>
      </nav>
      <section className="hero">
        <div className="eyebrow">Stay curious</div>
        <h1>Find your next horizon.</h1>
        <p>Thoughtful stays in places that give your plans room to unfold.</p>
        <div className="search">
          <label htmlFor="destination" className="sr-only">
            Destination
          </label>
          <input
            id="destination"
            value={destination}
            onChange={(e) => setDestination(e.target.value)}
            placeholder="Where are you going? Try Miami"
          />
          <button className="button" onClick={search}>
            Search stays
          </button>
        </div>
        {result && (
          <div className="banner" role="status">
            <strong>Made for your journey</strong>
            <br />
            {result}
          </div>
        )}
      </section>
    </main>
  );
}
