# ADR 0001: Modular monolith with a broker boundary

We use a Maven modular monolith so ownership boundaries are compile-time visible while the showcase remains simple to run. Event publication is behind an interface, allowing Redpanda in production-like profiles and synchronous in-process delivery in tests.

This preserves a future extraction seam without making local development depend on distributed deployment.
