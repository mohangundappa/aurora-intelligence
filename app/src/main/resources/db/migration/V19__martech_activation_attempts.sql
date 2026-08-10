create table martech_activation_attempts (
  attempt_id uuid primary key,
  proposal_id uuid not null references experiment_proposals(proposal_id),
  operation varchar(64) not null,
  destination_id varchar(255) not null,
  payload jsonb not null,
  idempotency_key varchar(255) not null,
  status varchar(32) not null,
  accepted_count integer not null,
  rejected_count integer not null,
  reason text,
  provider_metadata jsonb not null,
  attempted_at timestamptz not null
);

create index martech_activation_attempts_proposal_idx
  on martech_activation_attempts (proposal_id, attempted_at);
