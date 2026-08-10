#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
SEED_INSTANT="${SEED_INSTANT:-2026-08-10T03:00:00Z}"

if [[ "${1:-}" == "--reset" ]]; then
  docker compose exec -T postgres psql -U aurora -d aurora <<'SQL'
TRUNCATE raw_events, quarantined_events, derived_signals, decisions,
  experiment_exposures, experiment_outcomes, signal_lifecycle_audit,
  signal_lifecycle, identity_links, cdp_profiles, marketing_objective_audit,
  workflow_stage_timings, agent_tool_calls, agent_executions, marketing_insights,
  experiment_governance_audit, experiment_proposal_variants, experiment_proposals,
  experiment_definition_variants, experiment_definitions, marketing_objectives,
  martech_activation_attempts RESTART IDENTITY CASCADE;
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

for _ in range(60):
    health = subprocess.run(
        ["curl", "-fsS", f"{api}/actuator/health"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    if health.returncode == 0:
        break
    time.sleep(1)
else:
    raise RuntimeError(f"Aurora API did not become ready at {api}")

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

def request(method, path, body=None):
    command = ["curl", "-fsS", "-X", method, f"{api}{path}",
               "-H", "content-type: application/json"]
    if body is not None:
        command += ["-d", json.dumps(body)]
    output = subprocess.check_output(command)
    return json.loads(output) if output.strip() else None

headline = "demo-headline-miami"
headline_anon = "demo-anon-miami"
headline_corr = "demo-correlation-miami"
ingest([
    event(f"headline-{i}", name, headline, headline_anon, headline_corr, i, payload)
    for i, (name, payload) in enumerate([
        ("DESTINATION_SEARCHED", {"destination": "Miami"}),
        ("DESTINATION_SEARCHED", {"destination": "Miami"}),
        ("TRAVEL_DATES_SELECTED", {"checkIn": "2026-01-16", "checkOut": "2026-01-18"}),
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

# Give half of the synthetic sessions a deterministic family signal so the
# Insights Agent has comparable signal/no-signal groups with conversions.
for index in range(51, 100, 2):
    session = f"demo-experiment-{index:03d}"
    anonymous = f"demo-experiment-anon-{index:03d}"
    correlation = f"demo-experiment-correlation-{index:03d}"
    ingest([
        event(
            f"experiment-family-{index:03d}",
            "TRAVEL_PARTY_SELECTED",
            session,
            anonymous,
            correlation,
            200 + index,
            {"adults": 2, "children": 2},
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

# Build the governed workforce loop through the platform APIs. The generated
# proposal and analysis IDs are intentionally runtime-owned; the objective ID
# and all displayed business values remain stable across resets.
workforce_objective = "demo-workforce-miami"
request("POST", "/api/objectives", {
    "objectiveId": workforce_objective,
    "name": "Family traveler signal effect",
    "description": "Find evidence about family travelers.",
    "businessGoal": "Improve completed stays for family travelers",
    "targetKpi": "BOOKING_COMPLETED",
    "targetValue": 0.20,
    "targetAudience": "family travelers",
    "constraints": {"consentRequired": True, "providerNeutral": True},
    "startDate": "2026-08-01",
    "endDate": "2026-12-31",
    "createdBy": "seeded-demo"
})
request("POST", f"/api/objectives/{workforce_objective}/status/ACTIVE?actor=seeded-demo")
insight_execution = request("POST", f"/api/objectives/{workforce_objective}/insights")
insight_id = insight_execution["output"]["insightId"]
proposal_execution = request(
    "POST",
    f"/api/objectives/{workforce_objective}/experiment-proposals",
    {"insightId": insight_id},
)
proposal = proposal_execution["output"]
proposal_id = proposal["proposalId"]
request("POST", f"/api/experiment-proposals/{proposal_id}/approve", {
    "actor": "demo-marketer",
    "reason": "Approve the grounded proposal for the client walkthrough."
})
request("POST", f"/api/experiment-proposals/{proposal_id}/activate", {
    "actor": "demo-marketer",
    "reason": "Activate the approved draft for controlled measurement."
})
experiment_id = proposal["experimentId"]
request("POST", f"/api/experiments/{experiment_id}/deploy")

# Measurement ingress is provider-neutral and invokes ExperimentService rather
# than inserting exposure/outcome rows. Send enough deterministic subjects for
# both arms to clear the platform's 30-per-arm guard.
for index in range(100):
    request("POST", f"/api/experiments/{experiment_id}/exposures", {
        "anonymousId": f"demo-workforce-anon-{index:03d}",
        "customerId": None,
        "sessionId": f"demo-workforce-session-{index:03d}",
        "correlationId": f"demo-workforce-correlation-{index:03d}"
    })
exposures = request("GET", f"/api/experiments/{experiment_id}/exposures")
for exposure in exposures:
    exposure_index = int(exposure["correlationId"].rsplit("-", 1)[-1])
    if "personalized" in exposure["variant"] or (
        "control" in exposure["variant"] and exposure_index < 15
    ):
        request("POST", f"/api/experiments/{experiment_id}/outcomes", {
            "eventId": str(uuid.uuid5(uuid.NAMESPACE_URL, "aurora-demo:workforce-outcome:" + exposure["correlationId"])),
            "eventName": "BOOKING_COMPLETED",
            "eventTime": base.isoformat().replace("+00:00", "Z"),
            "receivedTime": base.isoformat().replace("+00:00", "Z"),
            "schemaVersion": "1.0",
            "source": "seeded-demo",
            "sessionId": exposure["sessionId"],
            "anonymousId": exposure["subjectId"],
            "customerId": None,
            "correlationId": exposure["correlationId"],
            "consent": {"analytics": True, "personalization": True},
            "payload": {"bookingId": "demo-workforce-booking"}
        })
request("POST", f"/api/experiments/{experiment_id}/analyses", {
    "objectiveId": workforce_objective,
    "correlationId": "demo-workforce-analysis"
})

# A separate objective deliberately has no relevant registered signal. The
# real insights runtime records a refusal; no fabricated weak analysis exists.
refusal_objective = "demo-workforce-refusal"
request("POST", "/api/objectives", {
    "objectiveId": refusal_objective,
    "name": "Explore an unsupported loyalty question",
    "description": "This objective intentionally has no registered evidence signal.",
    "businessGoal": "Understand an unsupported loyalty behavior",
    "targetKpi": "BOOKING_COMPLETED",
    "targetValue": 0.20,
    "targetAudience": "unsupported-loyalty-segment",
    "constraints": {"consentRequired": True},
    "startDate": "2026-01-01",
    "endDate": "2026-12-31",
    "createdBy": "seeded-demo"
})
request("POST", f"/api/objectives/{refusal_objective}/insights")

print("Seeded deterministic Aurora demo scenarios.")
print("Headline session:", headline)
print("Identity-stitch session:", stitch)
print("Experiment sessions: demo-experiment-000 through demo-experiment-099")
print("Workforce objective:", workforce_objective)
print("Workforce experiment:", experiment_id)
print("Refusal objective:", refusal_objective)
PY
