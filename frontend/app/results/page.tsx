"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { SiteHeader } from "../../components/SiteHeader";
import { properties } from "../../lib/catalog";
import { track } from "../../lib/tracker";

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
