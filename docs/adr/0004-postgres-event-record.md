# ADR 0004: PostgreSQL is the replayable raw-event record

Accepted events are retained as raw envelopes in PostgreSQL, while invalid events are retained in a quarantine table with a reason. The broker distributes events to consumers but is not the durable source of truth for replay.
