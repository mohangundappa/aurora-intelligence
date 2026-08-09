# ADR 0008: Stable experiment assignment across identity stitching

Assignment hashes the stable anonymous subject ID with experiment ID. When a
customer ID is later known, the anonymous ID remains the key, preventing a
mid-journey variant flip. Exposure and outcomes join through decision
`correlationId`; insufficient sample size withholds lift/significance claims.
