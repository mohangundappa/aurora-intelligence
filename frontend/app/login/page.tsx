"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { SiteHeader } from "../../components/SiteHeader";
import { sessionId, track } from "../../lib/tracker";

export default function Login() {
  const [identified, setIdentified] = useState(false);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const customerId = "demo-aurora-member";
    window.localStorage.setItem("aurora.customer", customerId);
    setIdentified(true);
    void track("CUSTOMER_IDENTIFIED", { customerId });
  }
  return (
    <main>
      <div className="shell">
        <SiteHeader />
        <section className="auth-card">
          <div className="eyebrow">Simulated account</div>
          <h1>Welcome back.</h1>
          {identified ? (
            <>
              <p className="lede">
                You are now identified as an Aurora Circle member. Return to
                your stay to see the loyalty rate.
              </p>
              <Link className="button" href={`/console?session=${sessionId()}`}>
                Open your journey
              </Link>
            </>
          ) : (
            <form className="booking-form" onSubmit={submit}>
              <label>
                Email
                <input
                  required
                  type="email"
                  defaultValue="traveler@example.test"
                />
              </label>
              <label>
                Password
                <input required type="password" defaultValue="showcase" />
              </label>
              <button className="button" type="submit">
                Sign in to demo account
              </button>
            </form>
          )}
        </section>
      </div>
    </main>
  );
}
