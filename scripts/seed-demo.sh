#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
SEED_INSTANT="${SEED_INSTANT:-2026-01-15T12:00:00Z}"

if [[ "${1:-}" == "--reset" ]]; then
  docker compose exec -T postgres psql -U aurora -d aurora <<'SQL'
TRUNCATE raw_events, quarantined_events, derived_signals, decisions,
  experiment_exposures, experiment_outcomes, signal_lifecycle_audit,
  signal_lifecycle, identity_links, cdp_profiles RESTART IDENTITY CASCADE;
SQL
fi

SEED_INSTANT="$SEED_INSTANT" API_URL="$API_URL" python3 - <<'PY'
import datetime
import json
import os
import subprocess
import time
import uuid

api = os.environ["API_URL"]
base = datetime.datetime.fromisoformat(os.environ["SEED_INSTANT"].replace("Z", "+00:00"))

def event(event_id, name, session, anonymous, correlation, offset, payload, customer=None):
    instant = (base + datetime.timedelta(minutes=offset)).isoformat().replace("+00:00", "Z")
    return {
        "eventId": str(uuid.uuid5(uuid.NAMESPACE_URL, "aurora-demo:" + event_id)),
        "eventName": name,
        "eventTime": instant,
        "receivedTime": instant,
        "schemaVersion": "1.0",
        "source": "seeded-demo",
        "sessionId": session,
        "anonymousId": anonymous,
        "customerId": customer,
        "correlationId": correlation,
        "consent": {"analytics": True, "personalization": True},
        "payload": payload,
    }

def ingest(events):
    payload = json.dumps(events)
    subprocess.run(
        ["curl", "-fsS", "-X", "POST", f"{api}/api/v1/events",
         "-H", "content-type: application/json", "-d", payload],
        check=True,
    )

headline = "demo-headline-miami"
headline_anon = "demo-anon-miami"
headline_corr = "demo-correlation-miami"
ingest([
    event(f"headline-{i}", name, headline, headline_anon, headline_corr, i, payload)
    for i, (name, payload) in enumerate([
        ("DESTINATION_SEARCHED", {"destination": "Miami"}),
        ("DESTINATION_SEARCHED", {"destination": "Miami"}),
        ("TRAVEL_PARTY_SELECTED", {"adults": 2, "children": 2}),
        ("TRAVEL_PARTY_SELECTED", {"adults": 2, "children": 2}),
        ("FILTER_APPLIED", {"filter": "pool", "value": "true"}),
        ("FILTER_APPLIED", {"filter": "resort", "value": "true"}),
        ("FILTER_APPLIED", {"filter": "budget", "value": "true"}),
        ("PROPERTY_VIEWED", {"propertyId": "aurora-miami"}),
        ("PROPERTY_VIEWED", {"propertyId": "aurora-miami"}),
        ("PROPERTY_VIEWED", {"propertyId": "aurora-miami"}),
        ("ROOM_VIEWED", {"propertyId": "aurora-miami", "roomId": "family-suite"}),
        ("RATE_VIEWED", {"propertyId": "aurora-miami", "roomId": "family-suite", "rate": 420}),
        ("BOOKING_STARTED", {"propertyId": "aurora-miami"}),
        ("BOOKING_ABANDONED", {"propertyId": "aurora-miami", "reason": "demo-abandonment"}),
    ])
])

stitch = "demo-identity-stitch"
stitch_anon = "demo-anon-stitch"
stitch_corr = "demo-correlation-stitch"
ingest([
    event("stitch-search", "DESTINATION_SEARCHED", stitch, stitch_anon, stitch_corr, 0, {"destination": "Miami"}),
    event("stitch-login", "CUSTOMER_IDENTIFIED", stitch, stitch_anon, stitch_corr, 2, {"customerId": "demo-customer-100"}, "demo-customer-100"),
])

for index in range(100):
    session = f"demo-experiment-{index:03d}"
    anonymous = f"demo-experiment-anon-{index:03d}"
    correlation = f"demo-experiment-correlation-{index:03d}"
    ingest([
        event(
            f"experiment-search-{index:03d}",
            "DESTINATION_SEARCHED",
            session,
            anonymous,
            correlation,
            10 + index,
            {"destination": "Miami"},
        )
    ])

# Trigger decision creation/exposure persistence after the consumer has caught up.
for index in range(100):
    session = f"demo-experiment-{index:03d}"
    for attempt in range(20):
        response = subprocess.run(
            ["curl", "-fsS", f"{api}/api/console/sessions/{session}"],
            capture_output=True,
        )
        if response.returncode == 0:
            break
        time.sleep(0.5)

# Seed outcomes after exposures exist. One half converts; all outcomes use
# the same correlation ID as the decision, making the join explicit.
for index in range(100):
    session = f"demo-experiment-{index:03d}"
    anonymous = f"demo-experiment-anon-{index:03d}"
    correlation = f"demo-experiment-correlation-{index:03d}"
    if index % 2 == 0:
        ingest([
            event(f"experiment-click-{index:03d}", "OFFER_CLICKED", session, anonymous, correlation, 100 + index, {"offerId": "MIAMI_GETAWAY"}),
            event(f"experiment-start-{index:03d}", "BOOKING_STARTED", session, anonymous, correlation, 101 + index, {"propertyId": "aurora-miami"}),
            event(f"experiment-complete-{index:03d}", "BOOKING_COMPLETED", session, anonymous, correlation, 102 + index, {"propertyId": "aurora-miami", "bookingId": f"demo-booking-{index:03d}"}),
        ])

print("Seeded deterministic Aurora demo scenarios.")
print("Headline session:", headline)
print("Identity-stitch session:", stitch)
print("Experiment sessions: demo-experiment-000 through demo-experiment-099")
PY
