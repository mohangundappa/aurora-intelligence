# ADR 0005: Structured signal attributes

Signals carry typed string attributes alongside numeric value and explanation.
Policy uses `attributeEquals` and numeric/freshness conditions. Human
explanation text is never a decision input, preventing copy changes from
changing eligibility.
