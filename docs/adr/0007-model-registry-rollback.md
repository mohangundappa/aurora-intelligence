# ADR 0007: Model registry and rollback

Model versions, feature weights, status, deployment timestamps, predictions,
and lifecycle audit rows are persisted. Serving always resolves the deployed
version; deploy promotes one version and rollback deploys a prior version.
Explanations expose per-feature contributions.
