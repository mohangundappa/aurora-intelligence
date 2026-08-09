# Aurora Hotels Customer Intelligence

Aurora is a fictional enterprise Marketing showcase for customer intelligence
that works **alongside**, rather than replacing, Adobe, Salesforce, Tealium,
Segment, or another CDP. The local simulator is a licence-free implementation
of the `CdpAdapter` seam; it is not a replacement CDP.

The showcase makes three business problems concrete:

1. **Development and rollout time:** reusable YAML signal definitions, calculator
   discovery, model evaluation, approval, deployment, rollback, and audit.
2. **Signal-to-decision time:** durable event ingestion, Redpanda distribution,
   near-real-time signal computation, Redis/PostgreSQL context, and configured
   decisions with explanations.
3. **Incremental value:** deterministic experiment assignment, exposure/outcome
   persistence, correlation-ID joins, aggregate funnel measurement, and an
   insufficient-sample guard.

The delivery comparison is a target calculated from visible assumptions. It is
not a measured commercial result.

## Prerequisites

- Docker Engine and Docker Compose
- Java 21 (the verified environment used 21.0.11)
- Maven 3.6+ (verified with 3.6.3)
- Node.js 20 (verified with 20.18.1) and npm
- Chromium for the optional Playwright suite
- At least 4 GB available to Docker for PostgreSQL, Redis, Redpanda, backend,
  and frontend

## One-command local showcase

From the repository root:

```bash
MAVEN_MIRROR_URL=https://repo.huaweicloud.com/repository/maven/ \
  docker compose up --build -d && ./scripts/seed-demo.sh --reset
```

`MAVEN_MIRROR_URL` is optional. It is a restricted-network workaround for Maven
Central HTTP 429 responses during the Docker backend build and must not be
committed as the default repository configuration.

Open:

- site: http://localhost:3000
- console: http://localhost:3000/console
- OpenAPI UI: http://localhost:8080/swagger-ui.html
- generated OpenAPI JSON: http://localhost:8080/v3/api-docs
- health: http://localhost:8080/actuator/health

There are no commercial credentials. The browser login is simulated. The
seeded identity scenario uses `demo-customer-100`; the browser login emits the
fixed demonstration customer `demo-aurora-member`. The default form values
are `traveler@example.test` / `showcase`; no credential is sent to a vendor and
no payment is taken.

## Demo data, reset, and reproducibility

```bash
./scripts/seed-demo.sh --reset
SEED_INSTANT=2026-01-15T12:00:00Z ./scripts/seed-demo.sh --reset
```

The script uses deterministic UUIDv5 event IDs and timestamps relative to
`SEED_INSTANT`. `--reset` truncates demo tables, including raw events,
quarantine, signals, decisions, experiments, profiles and identity links.
Running without reset is safe because event IDs are idempotent.

Scenarios:

- `demo-headline-miami`: Miami, two adults/two children, pool/resort/budget
  evidence, repeated property views, booking start and abandonment.
- `demo-identity-stitch`: anonymous search followed by explicit
  `CUSTOMER_IDENTIFIED`.
- `demo-experiment-000` through `demo-experiment-099`: synthetic volume for
  guarded and populated experiment views.

Seeded volume is synthetic demo data and must never be described as commercial
performance.

## Tests

Backend, including Testcontainers/Flyway:

```bash
mvn verify
```

Frontend:

```bash
cd frontend
npm ci
npm run lint
npm run build
```

Playwright against the running Compose stack:

```bash
cd frontend
npx playwright install chromium
npm run e2e
```

The Playwright job is intentionally gated in the current CI workflow because
it needs Docker Compose services and browser binaries. Backend/frontend CI runs
on pushes and pull requests; the documented local E2E command is deterministic.

## Repository layout

```text
common/       shared event, profile, signal, and decision records
ingest/       validation, quarantine, raw storage, publish, replay
cdp/          provider-neutral adapter and durable local simulator
identity/     explicit identity stitching and timeline
signals/      YAML discovery, calculators, snapshots, lifecycle
models/       registry, offline evaluation, serving, rollback, audit
decision/     configured policy and persisted decisions
experiments/  assignment, exposure/outcome join, measurement guard
context/      context cache, console APIs, funnel and operations
app/          Spring Boot, Flyway migrations, configuration and health
frontend/     Aurora site, console routes, typed API helper and E2E
scripts/      deterministic seed/reset
docs/         architecture, catalogs, ownership, API, roadmap and ADRs
```

## Troubleshooting

- **Maven 429:** rerun Compose with the documented `MAVEN_MIRROR_URL`.
- **Backend unhealthy:** run `docker compose ps` and
  `docker compose logs --tail=100 backend`; wait for PostgreSQL, Redis and
  Redpanda health checks before seeding.
- **Empty console:** run `./scripts/seed-demo.sh --reset`, wait for
  `/actuator/health`, then choose a backend session from the directory.
- **Stale demo rows:** use `--reset`; do not manually delete only one signal,
  because decisions and experiment joins may still reference its events.
- **Frontend build fails after dependency changes:** run `npm ci` in
  `frontend`; the committed lockfile is authoritative.
- **Playwright cannot launch:** run `npx playwright install chromium`.
- **Experiment guard remains visible:** fewer than 30 exposures in one arm is
  intentional; the UI must withhold lift rather than overclaim.

## Further reading

- [Architecture](docs/architecture.md)
- [Data flow](docs/data-flow.md)
- [Value thesis](docs/value-thesis.md)
- [Demo script](docs/demo-script.md)
- [Event catalog](docs/event-catalog.md)
- [Signal catalog](docs/signal-catalog.md)
- [API guide](docs/api.md)
- [Ownership boundaries](docs/ownership-boundaries.md)
- [Enterprise roadmap](docs/enterprise-roadmap.md)
