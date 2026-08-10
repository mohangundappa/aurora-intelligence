create table experiment_definitions (
  experiment_id varchar(120) primary key,
  name varchar(255) not null,
  description text not null,
  primary_outcome_event varchar(120) not null,
  minimum_exposures_per_variant integer not null check (minimum_exposures_per_variant > 0),
  lifecycle_status varchar(20) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table experiment_definition_variants (
  experiment_id varchar(120) not null references experiment_definitions(experiment_id) on delete cascade,
  variant_name varchar(120) not null,
  allocation_percentage integer not null check (allocation_percentage > 0),
  primary key (experiment_id, variant_name)
);
