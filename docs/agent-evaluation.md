# Agent evaluation framework

The version-controlled evaluation dataset is
`agents/src/main/resources/evaluation/agent-evaluation-dataset.json`. Each scenario
names an agent, an in-memory fixture, an expected refusal code when applicable, and
the obligations that must hold. The obligations deliberately check evidence
grounding, refusal boundaries, sample protection, observational wording, and the
read-only tool allowlist rather than matching implementation-specific prose. Refusal
codes are exact because their machine-readable contract is deterministic.

The build runs the suite with:

```bash
mvn -q -pl agents -am -Dtest=AgentEvaluationTest test
```

The test prints a human-readable pass/fail summary through the assertion failure
message, including each failed scenario and obligation. The fixtures are supplied
by an in-memory `AgentToolProvider`; the suite does not use the database, persist
agent executions or tool calls, record exposures, or mutate demo state.

To add a scenario, add a fixture in `AgentEvaluationTest`, add its scenario and
obligations to the JSON dataset, and keep the fixture seeded and isolated from the
demo database. If an obligation cannot be expressed without weakening the
contract, stop and raise that contract gap rather than lowering the assertion.

To evaluate another runtime, implement
`AgentEvaluationHarness.RuntimeAdapter` for that runtime and invoke the harness
with the same loaded dataset. The adapter must return the runtime's output or
refusal, cited evidence references, client-facing reasoning, and observed tool
calls. The harness then applies the same obligations, so replacing the deterministic
adapter with an LLM-backed adapter does not replace the governance bar.
