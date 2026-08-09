"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { SiteHeader } from "../../components/SiteHeader";
import { properties } from "../../lib/catalog";
import { rememberDecisionCorrelation, track } from "../../lib/tracker";

const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function Results() {
  const params =
    typeof window === "undefined"
      ? new URLSearchParams()
      : new URLSearchParams(window.location.search);
  const destination = params.get("destination") || "Miami";
  const [poolOnly, setPoolOnly] = useState(false);
  const [resortOnly, setResortOnly] = useState(false);
  const [businessOnly, setBusinessOnly] = useState(false);
  const [maxPrice, setMaxPrice] = useState(500);
  const [decision, setDecision] = useState<{
    action: string;
    experience: string;
    explanation: string;
    correlationId: string;
  } | null>(null);
  const [offerPresented, setOfferPresented] = useState(false);
  const session =
    typeof window === "undefined"
      ? ""
      : window.sessionStorage.getItem("aurora.session");
  useEffect(() => {
    if (!session) return;
    void fetch(`${API}/api/sessions/${session}/decision`)
      .then((response) => (response.ok ? response.json() : null))
      .then((value) => {
        if (!value) return;
        setDecision(value);
        rememberDecisionCorrelation(value.correlationId);
        void track(
          "OFFER_PRESENTED",
          {
            offerId: value.experience,
            experience: value.experience,
          },
          value.correlationId,
        );
        setOfferPresented(true);
      });
  }, [session]);
  const filtered = useMemo(
    () =>
      properties.filter(
        (property) =>
          property.destination.toLowerCase() === destination.toLowerCase() &&
          property.fromRate <= maxPrice &&
          (!poolOnly || property.amenities.includes("Pool")) &&
          (!resortOnly || property.type === "resort") &&
          (!businessOnly || property.type === "business"),
      ),
    [businessOnly, destination, maxPrice, poolOnly, resortOnly],
  );

  function applyFilter(filter: string, value: string) {
    void track("FILTER_APPLIED", { filter, value });
  }

  return (
    <main>
      <div className="shell">
        <SiteHeader />
        <div className="results-heading">
          <div>
            <div className="eyebrow">Your search</div>
            <h1>Stays in {destination}</h1>
            <p>{filtered.length} thoughtful places to begin.</p>
          </div>
          <Link className="text-link" href="/">
            Edit search
          </Link>
        </div>
        {decision && (
          <section
            className="experience-banner"
            aria-label="Personalized experience"
          >
            <div>
              <div className="eyebrow">Recommended for this journey</div>
              <h2>{decision.experience}</h2>
              <p>{decision.explanation}</p>
            </div>
            {offerPresented && (
              <button
                className="button button-small"
                type="button"
                onClick={() =>
                  void track(
                    "OFFER_CLICKED",
                    { offerId: decision.experience },
                    decision.correlationId,
                  )
                }
              >
                Explore this recommendation
              </button>
            )}
          </section>
        )}
        <div className="results-layout">
          <aside className="filters" aria-label="Filter results">
            <h2>Refine your stay</h2>
            <label className="check">
              <input
                type="checkbox"
                checked={poolOnly}
                onChange={(event) => {
                  setPoolOnly(event.target.checked);
                  applyFilter("pool", String(event.target.checked));
                }}
              />{" "}
              Pool
            </label>
            <label className="check">
              <input
                type="checkbox"
                checked={resortOnly}
                onChange={(event) => {
                  setResortOnly(event.target.checked);
                  applyFilter("resort", String(event.target.checked));
                }}
              />{" "}
              Resort
            </label>
            <label className="check">
              <input
                type="checkbox"
                checked={businessOnly}
                onChange={(event) => {
                  setBusinessOnly(event.target.checked);
                  applyFilter("business", String(event.target.checked));
                }}
              />{" "}
              Business hotel
            </label>
            <label htmlFor="price">
              Price per night <strong>${maxPrice}</strong>
            </label>
            <input
              id="price"
              type="range"
              min="150"
              max="500"
              step="10"
              value={maxPrice}
              onChange={(event) => {
                setMaxPrice(Number(event.target.value));
                applyFilter("price", event.target.value);
              }}
            />
          </aside>
          <section className="property-list" aria-label="Search results">
            {filtered.map((property) => (
              <article className="property-card" key={property.id}>
                <div className="property-art" aria-hidden="true">
                  <span>{property.destination}</span>
                </div>
                <div className="property-copy">
                  <div className="eyebrow">{property.type}</div>
                  <h2>{property.name}</h2>
                  <p>{property.description}</p>
                  <div className="amenities">
                    {property.amenities.map((amenity) => (
                      <span key={amenity}>{amenity}</span>
                    ))}
                  </div>
                  <div className="property-bottom">
                    <span>
                      From <strong>${property.fromRate}</strong> / night
                    </span>
                    <Link
                      className="button button-small"
                      href={`/property/${property.id}`}
                      onClick={() =>
                        void track("PROPERTY_VIEWED", {
                          propertyId: property.id,
                        })
                      }
                    >
                      View property
                    </Link>
                  </div>
                </div>
              </article>
            ))}
          </section>
        </div>
      </div>
    </main>
  );
}
