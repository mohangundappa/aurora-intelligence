"use client";

import Link from "next/link";
import { FormEvent, useEffect, useRef, useState } from "react";
import { notFound } from "next/navigation";
import { SiteHeader } from "../../../components/SiteHeader";
import { properties } from "../../../lib/catalog";
import { decisionCorrelation, track } from "../../../lib/tracker";

export default function BookingPage({
  params,
  searchParams,
}: {
  params: { id: string };
  searchParams: { room?: string };
}) {
  const property = properties.find((item) => item.id === params.id);
  const room = property?.rooms.find((item) => item.id === searchParams.room);
  const [complete, setComplete] = useState(false);
  const [startedAt] = useState(Date.now());
  const leaving = useRef(false);

  useEffect(() => {
    if (!property) return;
    const handleVisibility = () => {
      if (
        document.visibilityState === "hidden" &&
        !complete &&
        !leaving.current
      ) {
        leaving.current = true;
        void track("BOOKING_ABANDONED", {
          propertyId: property.id,
          reason: "intent-to-leave",
        });
      }
    };
    document.addEventListener("visibilitychange", handleVisibility);
    return () =>
      document.removeEventListener("visibilitychange", handleVisibility);
  }, [complete, property]);

  if (!property || !room) {
    return notFound();
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setComplete(true);
    void track(
      "BOOKING_COMPLETED",
      {
        propertyId: property?.id ?? params.id,
        bookingId: crypto.randomUUID(),
      },
      decisionCorrelation() ?? undefined,
    );
  }

  if (complete) {
    return (
      <main>
        <div className="shell">
          <SiteHeader />
          <section className="confirmation">
            <div className="eyebrow">Your stay is ready</div>
            <h1>See you at {property.name}.</h1>
            <p className="lede">
              Your simulated reservation is confirmed. No payment has been
              taken.
            </p>
            <Link className="button" href="/confirmation">
              View confirmation
            </Link>
          </section>
        </div>
      </main>
    );
  }

  return (
    <main>
      <div className="shell">
        <SiteHeader />
        <div className="booking-layout">
          <section>
            <div className="eyebrow">Complete your stay</div>
            <h1>{property.name}</h1>
            <p>
              {room.name} · ${room.rate} / night
            </p>
            <form className="booking-form" onSubmit={submit}>
              <label>
                First name
                <input required name="firstName" />
              </label>
              <label>
                Last name
                <input required name="lastName" />
              </label>
              <label>
                Email
                <input required type="email" name="email" />
              </label>
              <label>
                Arrival note
                <textarea
                  name="note"
                  rows={4}
                  placeholder="Anything that would make your arrival easier?"
                />
              </label>
              <button className="button" type="submit">
                Confirm simulated booking
              </button>
            </form>
          </section>
          <aside className="booking-summary">
            <span className="pill">DEMO BOOKING</span>
            <h2>A flexible start</h2>
            <p>
              We hold your room while you review the details. This fictional
              flow does not collect payment.
            </p>
            <button
              className="text-link"
              type="button"
              onClick={() => {
                leaving.current = true;
                void track("BOOKING_ABANDONED", {
                  propertyId: property.id,
                  reason: `presenter-trigger-${Date.now() - startedAt}ms`,
                });
              }}
            >
              Trigger presenter abandonment event
            </button>
          </aside>
        </div>
      </div>
    </main>
  );
}
