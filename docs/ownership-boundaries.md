# Ownership boundaries

Aurora complements a client's CDP; it does not replace one.

| Party | Responsibilities |
|---|---|
| CDP platform | Profile, identity, consent, audience, activation, and provider SLAs |
| CDP vendor/implementation partner | Adapter mapping, provider configuration, identity policy, and connector operations |
| Client IT | Networks, credentials, environments, security, data retention, and release controls |
| Our IT company | Signal/model/decision accelerator, integration code, tests, observability, and rollout support |
| Marketing team | Business definitions, eligibility/suppression rules, experiment hypotheses, approvals, and interpretation |

During migration/rollout we map the adapter contract, validate consent and
identity behavior, seed controlled scenarios, and run shadow comparisons. After
rollout the client IT/CDP operating model owns credentials, platform health,
retention, and provider changes; Marketing owns policy and measurement decisions;
our team supports the accelerator under an agreed service boundary.

The simulated CDP exists so this showcase needs no commercial CDP licence.
