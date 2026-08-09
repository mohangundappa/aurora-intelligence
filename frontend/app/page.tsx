"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { SiteHeader } from "../components/SiteHeader";
import { sessionId, track } from "../lib/tracker";

export default function Home() {
  const [destination, setDestination] = useState("");
  const [checkIn, setCheckIn] = useState("");
  const [checkOut, setCheckOut] = useState("");
  const [adults, setAdults] = useState("2");
  const [children, setChildren] = useState("0");

  useEffect(() => {
    void track("PAGE_VIEWED", { path: "/" });
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await track("DESTINATION_SEARCHED", { destination });
    if (checkIn && checkOut)
      await track("TRAVEL_DATES_SELECTED", { checkIn, checkOut });
    await track("TRAVEL_PARTY_SELECTED", {
      adults: Number(adults),
      children: Number(children),
    });
    window.location.href = `/results?destination=${encodeURIComponent(destination)}&checkIn=${checkIn}&checkOut=${checkOut}&adults=${adults}&children=${children}`;
  }

  return (
    <main>
      <div className="shell">
        <SiteHeader />
        <section className="hero hero-home">
          <div className="eyebrow">Stays with a little more sky</div>
          <h1>Find your next horizon.</h1>
          <p className="lede">
            Thoughtful stays in places that give your plans room to unfold.
          </p>
          <form className="search-panel" onSubmit={submit}>
            <div className="field field-wide">
              <label htmlFor="destination">Where are you going?</label>
              <input
                id="destination"
                required
                value={destination}
                onChange={(event) => setDestination(event.target.value)}
                placeholder="Try Miami"
              />
            </div>
            <div className="field">
              <label htmlFor="check-in">Check in</label>
              <input
                id="check-in"
                type="date"
                value={checkIn}
                onChange={(event) => setCheckIn(event.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="check-out">Check out</label>
              <input
                id="check-out"
                type="date"
                value={checkOut}
                onChange={(event) => setCheckOut(event.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="adults">Adults</label>
              <select
                id="adults"
                value={adults}
                onChange={(event) => setAdults(event.target.value)}
              >
                {[1, 2, 3, 4, 5, 6].map((value) => (
                  <option key={value}>{value}</option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="children">Children</label>
              <select
                id="children"
                value={children}
                onChange={(event) => setChildren(event.target.value)}
              >
                {[0, 1, 2, 3, 4].map((value) => (
                  <option key={value}>{value}</option>
                ))}
              </select>
            </div>
            <button className="button" type="submit">
              Search stays
            </button>
          </form>
          <p className="demo-note">
            A fictional hospitality showcase for exploring customer
            intelligence.
          </p>
        </section>
        <section className="feature-grid" aria-label="Aurora values">
          <article>
            <span className="feature-number">01</span>
            <h2>Room to gather</h2>
            <p>
              Spaces designed for the people and rituals that make a trip yours.
            </p>
          </article>
          <article>
            <span className="feature-number">02</span>
            <h2>Small moments</h2>
            <p>
              Local details, generous light, and service that knows when to step
              back.
            </p>
          </article>
          <article>
            <span className="feature-number">03</span>
            <h2>Stay curious</h2>
            <p>
              Go somewhere new, or see a familiar place from a different angle.
            </p>
          </article>
        </section>
        <footer className="site-footer">
          <span>AURORA HOTELS</span>
          <Link href="/console">Explore the intelligence console →</Link>
        </footer>
      </div>
    </main>
  );
}
