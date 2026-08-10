create table experiment_proposals (
  proposal_id uuid primary key,
  objective_id varchar(120) not null references marketing_objectives(objective_id),
  insight_id uuid not null references marketing_insights(insight_id),
  experiment_id varchar(120) not null unique,
  experiment_name varchar(255) not null,
  experiment_description text not null,
  hypothesis text not null,
  primary_outcome_event varchar(120) not null,
  minimum_exposures_per_variant integer not null check (minimum_exposures_per_variant > 0),
  expected_effect numeric not null check (expected_effect >= 0),
  reasoning text not null,
  evidence_refs jsonb not null,
  correlation_id varchar(255) not null,
  governance_state varchar(20) not null check (
    governance_state in ('PROPOSED','APPROVED','ACTIVATED','REJECTED')
  ),
  created_at timestamptz not null,
  updated_at timestamptz not null default now()
);

create table experiment_proposal_variants (
  proposal_id uuid not null references experiment_proposals(proposal_id) on delete cascade,
  variant_name varchar(120) not null,
  allocation_percentage integer not null check (allocation_percentage > 0),
  variant_order integer not null,
  primary key (proposal_id, variant_name),
  unique (proposal_id, variant_order)
);

create table experiment_governance_audit (
  audit_id bigserial primary key,
  proposal_id uuid not null references experiment_proposals(proposal_id),
  action varchar(30) not null,
  actor varchar(255) not null,
  from_state varchar(20) not null,
  to_state varchar(20) not null,
  reason text not null,
  created_at timestamptz not null default now()
);

create index experiment_proposals_objective_idx on experiment_proposals(objective_id);
create index experiment_proposals_insight_idx on experiment_proposals(insight_id);
create index experiment_governance_audit_proposal_idx on experiment_governance_audit(proposal_id);
