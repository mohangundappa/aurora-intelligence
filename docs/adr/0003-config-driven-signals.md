# ADR 0003: Configuration-driven signal definitions

Signal metadata and calculation intent live in versioned YAML under the signals module. The registry carries tier, inputs, confidence, freshness, consent, explanation, lifecycle, and owner metadata. Calculators will remain distinguishable as rule, aggregation, or model implementations.

This keeps signal governance editable without burying business logic in request handlers.
