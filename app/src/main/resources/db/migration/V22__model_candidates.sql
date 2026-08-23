create table model_candidates (
  candidate_id uuid primary key,
  model_name varchar(120) not null,
  package_hash varchar(255) not null,
  studio_initiative_id varchar(120) not null,
  package jsonb not null,
  status varchar(40) not null check (status = 'AWAITING_WEIGHTS'),
  created_at timestamptz not null default now(),
  unique (model_name, package_hash)
);

create index model_candidates_model_idx
  on model_candidates (model_name, created_at desc);

create table model_candidate_audit (
  audit_id bigserial primary key,
  candidate_id uuid not null references model_candidates(candidate_id),
  model_name varchar(120) not null,
  package_hash varchar(255) not null,
  action varchar(40) not null,
  created_at timestamptz not null default now()
);

create index model_candidate_audit_candidate_idx
  on model_candidate_audit (candidate_id, created_at desc);

create or replace function prevent_model_candidate_audit_mutation()
returns trigger
language plpgsql
as $$
begin
  raise exception 'model_candidate_audit is append-only';
end;
$$;

create trigger model_candidate_audit_append_only
before update or delete on model_candidate_audit
for each row execute function prevent_model_candidate_audit_mutation();
