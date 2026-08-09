export const EVENT_TYPES = [
  "PAGE_VIEWED",
  "DESTINATION_SEARCHED",
  "TRAVEL_DATES_SELECTED",
  "TRAVEL_PARTY_SELECTED",
  "FILTER_APPLIED",
  "PROPERTY_VIEWED",
  "ROOM_VIEWED",
  "RATE_VIEWED",
  "BOOKING_STARTED",
  "BOOKING_ABANDONED",
  "BOOKING_COMPLETED",
  "CUSTOMER_IDENTIFIED",
  "OFFER_PRESENTED",
  "OFFER_CLICKED",
] as const;

export type EventName = (typeof EVENT_TYPES)[number];

type EventPayload = Record<string, string | number | boolean>;

const sessionKey = "aurora.session";
const anonymousKey = "aurora.anonymous";

export function sessionId(): string {
  if (typeof window === "undefined") return "";
  const existing = window.sessionStorage.getItem(sessionKey);
  if (existing) return existing;
  const created = crypto.randomUUID();
  window.sessionStorage.setItem(sessionKey, created);
  return created;
}

function anonymousId(): string {
  if (typeof window === "undefined") return "";
  const existing = window.localStorage.getItem(anonymousKey);
  if (existing) return existing;
  const created = crypto.randomUUID();
  window.localStorage.setItem(anonymousKey, created);
  return created;
}

export async function track(
  name: EventName,
  payload: EventPayload,
): Promise<void> {
  if (typeof window === "undefined" || !EVENT_TYPES.includes(name)) return;
  const correlationId = crypto.randomUUID();
  const now = new Date().toISOString();
  await fetch(
    `${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"}/api/v1/events`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Correlation-Id": correlationId,
      },
      body: JSON.stringify({
        eventId: crypto.randomUUID(),
        eventName: name,
        eventTime: now,
        receivedTime: now,
        schemaVersion: "1.0",
        source: "aurora-web",
        sessionId: sessionId(),
        anonymousId: anonymousId(),
        customerId: window.localStorage.getItem("aurora.customer"),
        correlationId,
        consent: { analytics: true, personalization: true },
        payload,
      }),
    },
  );
}
