# Enterprise roadmap

## MVP versus production

The MVP uses a local simulated CDP, one Compose topology, a baseline scoring
model, a basic drift indicator, and persisted-timestamp lag approximation.
Login, booking, and the seeded experiment are simulated. These are not
production claims.

Production should replace the simulator through `CdpAdapter` implementations
for Adobe, Salesforce, Tealium, or Segment, map provider consent/identity
semantics, use managed Kafka/Redpanda and PostgreSQL, add tenant isolation,
secrets management, encryption, retention/deletion, RBAC, and provider SLAs.

At high volume, partition raw records, scale consumers horizontally, separate
signal/model workers, add an analytical warehouse for aggregate measurement,
and add schema registry/data contracts. Governance must cover signal ownership,
policy approval, model promotion, audit retention, and experiment review.
