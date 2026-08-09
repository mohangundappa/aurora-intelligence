"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { SiteHeader } from "../../../components/SiteHeader";
import { properties } from "../../../lib/catalog";
import { track } from "../../../lib/tracker";

export default function PropertyPage({ params }: { params: { id: string } }) {
  const property =
    properties.find((item) => item.id === params.id) ?? properties[0];
  const [identified, setIdentified] = useState(false);

  useEffect(() => {
    setIdentified(Boolean(window.localStorage.getItem("aurora.customer")));
    void track("PROPERTY_VIEWED", { propertyId: property.id });
  }, [property.id]);

  return (
    <main>
      <div className="shell">
        <SiteHeader />
        <section className="property-hero">
          <div className="property-art property-art-large" aria-hidden="true">
            <span>{property.destination}</span>
          </div>
          <div>
            <div className="eyebrow">
              {property.type} · {property.destination}
            </div>
            <h1>{property.name}</h1>
            <p className="lede">{property.description}</p>
            <div className="amenities">
              {property.amenities.map((amenity) => (
                <span key={amenity}>{amenity}</span>
              ))}
            </div>
          </div>
        </section>
        <section className="rooms-section">
          <div className="section-heading">
            <div>
              <div className="eyebrow">Make it yours</div>
              <h2>Choose a room</h2>
            </div>
            <p>
              Rates are shown per night and include our flexible cancellation
              promise.
            </p>
          </div>
          <div className="room-list">
            {property.rooms.map((room) => (
              <article className="room-card" key={room.id}>
                <div>
                  <h3>{room.name}</h3>
                  <p>{room.description}</p>
                  <button
                    className="text-link"
                    onClick={() =>
                      void track("ROOM_VIEWED", {
                        propertyId: property.id,
                        roomId: room.id,
                      })
                    }
                  >
                    See room details
                  </button>
                </div>
                <div className="room-rate">
                  <span>${room.rate} / night</span>
                  {identified && (
                    <small className="loyalty">
                      Aurora Circle rate · ${Math.round(room.rate * 0.9)}
                    </small>
                  )}
                  <Link
                    className="button button-small"
                    href={`/booking/${property.id}?room=${room.id}`}
                    onClick={() => {
                      void track("RATE_VIEWED", {
                        propertyId: property.id,
                        roomId: room.id,
                        rate: room.rate,
                      });
                      void track("BOOKING_STARTED", {
                        propertyId: property.id,
                      });
                    }}
                  >
                    Select room
                  </Link>
                </div>
              </article>
            ))}
          </div>
        </section>
      </div>
    </main>
  );
}
