# Aurora Hotels Customer Intelligence

Aurora is a fictional enterprise Marketing showcase for customer intelligence
that works **alongside**, rather than replacing, Adobe, Salesforce, Tealium,
Segment, or another CDP. The local CDP simulator is only a licence-free stand-in
for a provider adapter.

The three business problems demonstrated are:

1. shortening model and signal development/rollout time (an assumption-derived
   target, not a measured commercial claim);
2. shortening signal-to-decision time through persisted events, calculators,
   context, and policy decisioning;
3. measuring incremental value with deterministic experiments and guardrails.

## Prerequisites

Docker Compose, Docker, Java 21, Maven, Node 20, and a browser. The default
stack uses PostgreSQL 16, Redis 7, and Redpanda.

## Run the showcase

```bash
MAVEN_MIRROR_URL=https://repo.huaweicloud.com/repository/maven/ \
  docker compose up --build -d
./scripts/seed-demo.sh --reset
```

`MAVEN_MIRROR_URL` is an optional restricted-network workaround for Docker
builds that receive Maven Central HTTP 429 responses. It is not committed as a
repository default.

Open:

- Aurora site: http://localhost:3000
- Marketing Intelligence Console: http://localhost:3000/console
- OpenAPI UI: http://localhost:8080/swagger-ui.html
- OpenAPI document: http://localhost:8080/v3/api-docs

There are no commercial demo credentials. The login flow is simulated and the
seeded customer is `demo-customer-100`.

## Seed and reset

`scripts/seed-demo.sh --reset` truncates demo tables, applies fixed event IDs
and timestamps relative to `SEED_INSTANT`, and creates:

- the headline anonymous Miami family-resort abandonment journey;
- an anonymous-to-known identity-stitch journey;
- a converted booking journey;
- 70 synthetic experiment sessions (clearly synthetic volume).

Run without `--reset` to replay the same idempotent seed. Use `SEED_INSTANT`
to reproduce timestamps in another deterministic run.

See [docs/demo-script.md](docs/demo-script.md) for the 10–15 minute walkthrough.
See [docs/architecture.md](docs/architecture.md) for the system boundaries and
[docs/enterprise-roadmap.md](docs/enterprise-roadmap.md) for production gaps.
