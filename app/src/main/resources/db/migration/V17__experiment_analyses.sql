create table experiment_analyses (
  analysis_id uuid primary key,
  experiment_id varchar(120) not null,
  variant_results jsonb not null,
  sufficient_sample boolean not null,
  absolute_lift numeric,
  relative_lift numeric,
  recommendation varchar(40),
  reasoning text not null,
  evidence_refs jsonb not null,
  correlation_id varchar(255) not null,
  produced_at timestamptz not null
);

create index experiment_analyses_experiment_idx
  on experiment_analyses(experiment_id, produced_at);
